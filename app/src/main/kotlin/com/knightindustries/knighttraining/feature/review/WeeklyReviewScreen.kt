package com.knightindustries.knighttraining.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.WeeklyReview
import com.knightindustries.knighttraining.core.store.WeekReviewContext
import com.knightindustries.knighttraining.core.store.saveWeeklyReview
import com.knightindustries.knighttraining.core.store.weekReviewContext
import com.knightindustries.knighttraining.feature.home.weekRange
import com.knightindustries.knighttraining.ui.activityName
import com.knightindustries.knighttraining.ui.durationText
import kotlinx.coroutines.launch
import java.util.UUID

/** iOS `WeeklyReviewViewModel` without the coach, which arrives with the on-device model. */
class WeeklyReviewViewModel(private val container: AppContainer, private val weeklyPlanId: UUID) : ViewModel() {
    var context by mutableStateOf<WeekReviewContext?>(null)
        private set
    var wentWell by mutableStateOf("")
    var heldBack by mutableStateOf("")
    var wishIDone by mutableStateOf("")
    var intent by mutableStateOf<String?>(null)
    var isSaving by mutableStateOf(false)
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

    /** Every field is optional: a mostly empty reflection still saves. */
    fun save(onSaved: () -> Unit) {
        val current = context ?: return
        isSaving = true
        viewModelScope.launch {
            try {
                val base = current.existing ?: WeeklyReview(weeklyPlanId = weeklyPlanId)
                container.store.saveWeeklyReview(base.updating(wentWell.clean(), heldBack.clean(), wishIDone.clean(), intent))
                container.preferences.setLastReviewedWeekStart(current.plan.weekStart)
                onSaved()
            } finally {
                isSaving = false
            }
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

@Composable
private fun ReflectionField(value: String, label: Int, hint: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(stringResource(label)) },
        placeholder = { Text(stringResource(hint)) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
    )
}

private fun intentName(code: String): Int = when (code) {
    WeeklyReview.NextWeekIntent.KEEP_SAME -> R.string.intent_keep_same
    WeeklyReview.NextWeekIntent.MAKE_EASIER -> R.string.intent_make_easier
    WeeklyReview.NextWeekIntent.PUSH_HARDER -> R.string.intent_push_harder
    else -> R.string.intent_change_focus
}
