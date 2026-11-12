package com.knightindustries.knighttraining.feature.review

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.coach.CoachTargetChange
import com.knightindustries.knighttraining.core.coach.WeeklyTip
import com.knightindustries.knighttraining.core.model.PlanSuggestionEngine
import com.knightindustries.knighttraining.core.model.PlanSuggestionHistory
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.model.WeeklyReview
import com.knightindustries.knighttraining.core.store.WeekReviewContext
import com.knightindustries.knighttraining.core.store.applyCoachTarget
import com.knightindustries.knighttraining.core.store.saveWeeklyReview
import com.knightindustries.knighttraining.core.store.setNextWeekFocus
import com.knightindustries.knighttraining.core.store.weekReviewContext
import com.knightindustries.knighttraining.feature.home.weekRange
import com.knightindustries.knighttraining.ui.activityName
import com.knightindustries.knighttraining.ui.durationText
import com.knightindustries.knighttraining.ui.targetText
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.launch

/** iOS `WeeklyReviewViewModel`: the reflection, then (on a supported phone) the coach's tip. */
class WeeklyReviewViewModel(private val container: AppContainer, private val weeklyPlanId: UUID) : ViewModel() {
    sealed interface Phase {
        data object Form : Phase
        data object PreparingTip : Phase
        data class Tip(val tip: WeeklyTip, val accepted: Boolean = false) : Phase
    }

    var context by mutableStateOf<WeekReviewContext?>(null)
        private set
    var wentWell by mutableStateOf("")
    var heldBack by mutableStateOf("")
    var wishIDone by mutableStateOf("")
    var intent by mutableStateOf<String?>(null)
    var isSaving by mutableStateOf(false)
        private set
    var phase by mutableStateOf<Phase>(Phase.Form)
        private set

    init {
        viewModelScope.launch {
            val loaded = container.store.weekReviewContext(weeklyPlanId, container.preferences.firstLaunch()) ?: return@launch
            loaded.existing?.let {
                wentWell = it.wentWell.orEmpty()
                heldBack = it.heldBack.orEmpty()
                wishIDone = it.wishIDone.orEmpty()
                intent = it.nextWeekIntent
            }
            context = loaded
        }
    }

    /**
     * Every field is optional: a mostly empty reflection still saves. Then the coach gets its turn,
     * and [onSaved] runs straight away when it has nothing to say.
     */
    fun save(onSaved: () -> Unit) {
        val current = context ?: return
        isSaving = true
        viewModelScope.launch {
            val review: WeeklyReview
            try {
                val base = current.existing ?: WeeklyReview(weeklyPlanId = weeklyPlanId)
                review = base.updating(wentWell.clean(), heldBack.clean(), wishIDone.clean(), intent)
                container.store.saveWeeklyReview(review)
                container.preferences.setLastReviewedWeekStart(current.plan.weekStart)
            } finally {
                isSaving = false
            }
            val service = container.weeklyTipService
            if (!service.isEnabledInThisBuild) return@launch onSaved()
            phase = Phase.PreparingTip
            val tip = service.generateTip(current.plan, review, current.notedActivities, current.previous?.nextWeekFocus)
            if (tip == null) {
                phase = Phase.Form
                return@launch onSaved()
            }
            phase = Phase.Tip(tip)
            if (tip.suggestsCoach) service.recordCoachSuggestionShown()
        }
    }

    /**
     * Shows a given tip as if the coach had written it (iOS `showTipForPreview`): for UI tests,
     * since no emulator runs Gemini Nano.
     */
    @VisibleForTesting
    fun showTipForPreview(tip: WeeklyTip) {
        phase = Phase.Tip(tip)
    }

    /** Saves the coach's focus and target change onto this review; "Not now" saves neither. */
    fun acceptTip() {
        val current = context ?: return
        val shown = phase as? Phase.Tip ?: return
        viewModelScope.launch {
            val tip = shown.tip
            tip.focus?.let { container.store.setNextWeekFocus(it, weeklyPlanId) }
            val code = tip.targetActivityTypeCode
            val value = tip.targetValue
            if (code != null && value != null) {
                val firstLaunch = container.preferences.firstLaunch()
                container.store.applyCoachTarget(
                    PlanSuggestionHistory.CoachTarget(code, value),
                    reviewedWeeklyPlanId = weeklyPlanId,
                    goalTemplateId = CoachTargetChange.goal(code, current.plan)?.goalTemplateId,
                    currentWeekStart = WeeklyPlan.effectiveWeekStart(Instant.now(), firstLaunch, WeekCalendar.system()),
                )
            }
            phase = shown.copy(accepted = true)
        }
    }

    /** Closing without saving still counts as offered, so launch doesn't ask again for this week. */
    fun dismiss(onDismissed: () -> Unit) {
        val current = context
        viewModelScope.launch {
            current?.let { container.preferences.setLastReviewedWeekStart(it.plan.weekStart) }
            onDismissed()
        }
    }

    private fun String.clean() = trim().ifEmpty { null }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WeeklyReviewScreen(viewModel: WeeklyReviewViewModel, onDone: () -> Unit) {
    val context = viewModel.context
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context?.let { stringResource(R.string.review_title, it.weekNumber) } ?: "") },
                navigationIcon = { IconButton(onClick = { viewModel.dismiss(onDone) }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (context == null) {
            CircularProgressIndicator(Modifier.padding(padding).padding(32.dp))
            return@Scaffold
        }
        when (val phase = viewModel.phase) {
            WeeklyReviewViewModel.Phase.PreparingTip -> {
                Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.review_preparing_tip), modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Scaffold
            }
            is WeeklyReviewViewModel.Phase.Tip -> {
                TipCard(phase, Modifier.padding(padding), onAccept = viewModel::acceptTip, onContinue = onDone)
                return@Scaffold
            }
            WeeklyReviewViewModel.Phase.Form -> Unit
        }
        val plan = context.plan
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(weekRange(plan.weekStart, context.weekEnd), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(pluralStringResource(R.plurals.review_goals_completed, plan.goalCount, plan.completedGoalCount, plan.goalCount), style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { (plan.completionPercentage / 100).toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.review_time_invested, durationText(context.timeInvestedSeconds)), style = MaterialTheme.typography.bodyMedium)
                }
            }

            context.previous?.let { previous ->
                val quotes = listOfNotNull(previous.heldBack, previous.wishIDone)
                if (quotes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.review_last_week), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        quotes.forEach { Text("“$it”", style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            if (context.notedActivities.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.review_notes), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    context.notedActivities.forEach { Text("${activityName(it.activityType.code)}: ${it.notes}", style = MaterialTheme.typography.bodyMedium) }
                }
            }

            ReflectionField(viewModel.wentWell, R.string.review_went_well, R.string.review_went_well_hint) { viewModel.wentWell = it }
            ReflectionField(viewModel.heldBack, R.string.review_held_back, R.string.review_held_back_hint) { viewModel.heldBack = it }
            ReflectionField(viewModel.wishIDone, R.string.review_wish, R.string.review_wish_hint) { viewModel.wishIDone = it }

            Text(stringResource(R.string.review_next_week), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeeklyReview.NextWeekIntent.all.forEach { code ->
                    FilterChip(
                        selected = viewModel.intent == code,
                        onClick = { viewModel.intent = if (viewModel.intent == code) null else code },
                        label = { Text(stringResource(intentName(code))) },
                    )
                }
            }
            Button(onClick = { viewModel.save(onDone) }, enabled = !viewModel.isSaving, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

/**
 * The coach's tip, with its focus and target change when it offered them. The primary button
 * saves both ("Use it next week" with a target, else "Make it my focus"), then becomes Continue;
 * "Not now" leaves without saving. A bare tip just has Continue.
 */
@Composable
private fun TipCard(phase: WeeklyReviewViewModel.Phase.Tip, modifier: Modifier, onAccept: () -> Unit, onContinue: () -> Unit) {
    val tip = phase.tip
    val hasTarget = tip.targetActivityTypeCode != null && tip.targetValue != null
    val offersAdvice = (tip.focus != null || hasTarget) && !phase.accepted
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.review_tip_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(tip.text, style = MaterialTheme.typography.bodyLarge)
                tip.focus?.let { Advice(stringResource(R.string.review_tip_focus), it) }
                if (hasTarget) {
                    val code = tip.targetActivityTypeCode!!
                    val type = PlanSuggestionEngine.targetType(code)
                    val target = targetText(code, type, tip.targetValue!!)
                    // A count already names the activity ("40 puzzles"); a duration doesn't.
                    Advice(stringResource(R.string.review_tip_target), if (type == TargetType.COUNT) target else "${activityName(code)}: $target")
                }
            }
        }
        Button(onClick = if (offersAdvice) onAccept else onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    when {
                        !offersAdvice -> R.string.review_tip_continue
                        hasTarget -> R.string.review_tip_use
                        else -> R.string.review_tip_make_focus
                    },
                ),
            )
        }
        if (offersAdvice) {
            TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.review_tip_not_now)) }
        }
    }
}

@Composable
private fun Advice(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider()
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReflectionField(value: String, label: Int, hint: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(stringResource(label)) },
        placeholder = { Text(stringResource(hint)) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
    )
}

internal fun intentName(code: String): Int = when (code) {
    WeeklyReview.NextWeekIntent.KEEP_SAME -> R.string.intent_keep_same
    WeeklyReview.NextWeekIntent.MAKE_EASIER -> R.string.intent_make_easier
    WeeklyReview.NextWeekIntent.PUSH_HARDER -> R.string.intent_push_harder
    else -> R.string.intent_change_focus
}
