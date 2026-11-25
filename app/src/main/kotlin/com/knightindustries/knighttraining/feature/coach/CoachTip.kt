package com.knightindustries.knighttraining.feature.coach

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.coach.CoachTargetChange
import com.knightindustries.knighttraining.core.model.PlanSuggestionEngine
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.model.WeeklyReview.CoachTip
import com.knightindustries.knighttraining.core.store.applyCoachTarget
import com.knightindustries.knighttraining.core.store.previousWeeklyPlan
import com.knightindustries.knighttraining.core.store.setCoachTipStatus
import com.knightindustries.knighttraining.core.store.setNextWeekFocus
import com.knightindustries.knighttraining.core.store.setNextWeekTarget
import com.knightindustries.knighttraining.core.store.weeklyReview
import com.knightindustries.knighttraining.ui.activityName
import com.knightindustries.knighttraining.ui.targetText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The weekly coach's tip about last week, shown where this week is planned (iOS
 * `CoachTipViewModel`). Owned by the screen's ViewModel, which passes its scope.
 *
 * "Use it" means something different in each [Placement]:
 * - [Placement.PLANNING] (Plan This Week): no week is committed yet, so an accepted target is only
 *   remembered on the review; committing any plan this week applies it (`acceptedCoachTarget`,
 *   read by every commit). An accepted tip stays on screen, so the user sees what's about to change.
 * - [Placement.COMMITTED_WEEK] (Home's Last week tile, when the week was committed before the
 *   review ever showed): an accepted target changes the plan now, while the week is still editable
 *   (`applyCoachTarget`). Only a tip still waiting for an answer is shown.
 */
class CoachTipState(private val container: AppContainer, val placement: Placement, private val scope: CoroutineScope) {
    enum class Placement { PLANNING, COMMITTED_WEEK }

    sealed interface State {
        data object Hidden : State

        /** The coach is still writing (planning only; Home never shows a spinner). */
        data object Loading : State

        data class Shown(val tip: CoachTip) : State
    }

    var state by mutableStateOf<State>(State.Hidden)
        private set
    private var reviewedPlan: WeeklyPlan? = null

    /** Loads the tip for last week's review, asking the coach first if nobody has yet. */
    suspend fun load() {
        val generator = container.weeklyCoachTipGenerator
        val store = container.store
        val firstLaunch = container.preferences.firstLaunch()
        val plan = store.previousWeeklyPlan(firstLaunch = firstLaunch)
        var review = plan?.let { store.weeklyReview(it.id) }
        if (!generator.isEnabled || plan == null || review == null) {
            state = State.Hidden
            return
        }
        reviewedPlan = plan
        if (review.coachTip == null) {
            if (placement == Placement.PLANNING) state = State.Loading
            generator.generateIfNeeded(plan)
            review = store.weeklyReview(plan.id)
        }
        state = visibleState(review?.coachTip, placement)
        val shown = (state as? State.Shown)?.tip
        // The three-month wait starts once a coach suggestion is actually on screen.
        if (shown?.status == CoachTip.Status.READY && shown.suggestsCoach) container.weeklyTipService.recordCoachSuggestionShown()
    }

    fun hide() {
        state = State.Hidden
    }

    /** "Use it": saves the focus and target on last week's review. See [Placement] for the target. */
    fun accept() {
        val tip = (state as? State.Shown)?.tip?.takeIf { it.status == CoachTip.Status.READY } ?: return
        val plan = reviewedPlan ?: return
        scope.launch {
            try {
                val store = container.store
                tip.focus?.let { store.setNextWeekFocus(it, plan.id) }
                tip.target?.let { target ->
                    when (placement) {
                        Placement.PLANNING -> store.setNextWeekTarget(target, plan.id)
                        Placement.COMMITTED_WEEK -> store.applyCoachTarget(
                            target,
                            reviewedWeeklyPlanId = plan.id,
                            goalTemplateId = CoachTargetChange.goal(target.activityTypeCode, plan)?.goalTemplateId,
                            currentWeekStart = WeeklyPlan.effectiveWeekStart(Instant.now(), container.preferences.firstLaunch(), WeekCalendar.system()),
                        )
                    }
                }
                store.setCoachTipStatus(CoachTip.Status.ACCEPTED, plan.id)
                state = if (placement == Placement.PLANNING) State.Shown(tip.copy(status = CoachTip.Status.ACCEPTED)) else State.Hidden
            } catch (failure: Exception) {
                Log.e("WeeklyCoach", "Failed to accept the weekly coach tip", failure)
            }
        }
    }

    /** "Not now" / "Got it": hides the tip for good, saving nothing. */
    fun dismiss() {
        val plan = reviewedPlan ?: return
        state = State.Hidden
        scope.launch { container.store.setCoachTipStatus(CoachTip.Status.DISMISSED, plan.id) }
    }

    companion object {
        fun visibleState(tip: CoachTip?, placement: Placement): State {
            if (tip?.text == null) return State.Hidden
            return when (tip.status) {
                CoachTip.Status.READY -> State.Shown(tip)
                CoachTip.Status.ACCEPTED -> if (placement == Placement.PLANNING) State.Shown(tip) else State.Hidden
                CoachTip.Status.UNAVAILABLE, CoachTip.Status.DISMISSED -> State.Hidden
            }
        }
    }
}

/** iOS `CoachTipCard`. Draws nothing while there's no tip. */
@Composable
fun CoachTipCard(state: CoachTipState, modifier: Modifier = Modifier) {
    AnimatedContent(state.state, modifier, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "coachTip") { current ->
        when (current) {
            CoachTipState.State.Hidden -> Unit
            CoachTipState.State.Loading -> TipCardFrame {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.coach_tip_loading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is CoachTipState.State.Shown -> TipCardFrame { TipContent(current.tip, onAccept = state::accept, onDismiss = state::dismiss) }
        }
    }
}

@Composable
private fun TipCardFrame(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.review_tip_title), style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun TipContent(tip: CoachTip, onAccept: () -> Unit, onDismiss: () -> Unit) {
    val accepted = tip.status == CoachTip.Status.ACCEPTED
    Text(tip.text.orEmpty(), style = MaterialTheme.typography.bodyLarge)
    tip.focus?.let { Advice(stringResource(R.string.review_tip_focus), it, accepted) }
    tip.target?.let { target ->
        val code = target.activityTypeCode
        val type = PlanSuggestionEngine.targetType(code)
        val text = targetText(code, type, target.value)
        // A count already names the activity ("40 puzzles"); a duration doesn't.
        Advice(stringResource(R.string.review_tip_target), if (type == TargetType.COUNT) text else "${activityName(code)}: $text", accepted)
    }
    if (tip.status != CoachTip.Status.READY) return
    if (tip.offersAdvice) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.review_tip_not_now)) }
            Button(onClick = onAccept, modifier = Modifier.weight(1f)) {
                Text(stringResource(if (tip.target != null) R.string.review_tip_use else R.string.review_tip_make_focus))
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.review_tip_got_it)) }
        }
    }
}

@Composable
private fun Advice(label: String, text: String, accepted: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (accepted) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.coach_tip_saved), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
