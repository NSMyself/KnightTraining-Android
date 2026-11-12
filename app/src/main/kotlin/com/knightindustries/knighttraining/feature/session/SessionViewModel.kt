package com.knightindustries.knighttraining.feature.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.core.model.CoachingTopic
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.session.PracticeSession
import com.knightindustries.knighttraining.core.session.PracticeSession.Attribution
import com.knightindustries.knighttraining.core.store.goalCandidates
import com.knightindustries.knighttraining.core.store.logPracticeSession
import com.knightindustries.knighttraining.core.store.sessionWeekGoals
import com.knightindustries.knighttraining.core.store.weeklyGoal
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

sealed interface SessionEvent {
    /** Saved, discarded, or stopped too soon to keep: the screen leaves. */
    data class Finished(val discardedAsAccidental: Boolean = false) : SessionEvent

    data object SaveFailed : SessionEvent
}

/**
 * Drives the timer and the review after Stop (iOS `StudyingViewModel`). The session itself lives
 * in [com.knightindustries.knighttraining.session.SessionRepository], so leaving this screen never
 * ends it.
 */
class SessionViewModel(private val container: AppContainer) : ViewModel() {
    private val sessions = container.sessions
    val session: StateFlow<PracticeSession?> = sessions.session

    /** Goals the session refers to: the current one and those "Change" offers. */
    val goals = mutableStateMapOf<UUID, WeeklyGoal>()

    /** The goals of the week the session started in: the review's "Counts toward" candidates. */
    var weekGoals by mutableStateOf<List<WeeklyGoal>>(emptyList())
        private set

    private val events = Channel<SessionEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val current = session.value ?: return@launch
            val ids = buildList {
                (current.attribution as? Attribution.Goal)?.let { add(it.goalId) }
                addAll(current.availableGoalIds)
                current.completedSegments.mapNotNull { (it.attribution as? Attribution.Goal)?.goalId }.let(::addAll)
            }.distinct()
            ids.forEach { id -> container.store.weeklyGoal(id)?.let { goals[id] = it } }
            weekGoals = container.store.sessionWeekGoals(current, container.preferences.firstLaunch())
        }
    }

    fun pause() = sessions.pause()

    fun resume() = sessions.resume()

    /** Under 10 seconds, Stop is an accidental tap: discard without a review (iOS). */
    fun stop() {
        val now = Instant.now()
        val current = session.value ?: return
        if (current.isBelowAccidentalStopThreshold(now)) {
            sessions.clear()
            events.trySend(SessionEvent.Finished(discardedAsAccidental = true))
        } else {
            sessions.stop()
        }
    }

    /**
     * The goals the running segment could count toward, asked when switching away from it since
     * it never reaches the review. Only a "Something else" segment has any: a plan goal's segment
     * already moves its goal, and general practice has no category to match.
     */
    fun outgoingCandidates(): List<WeeklyGoal> {
        val current = session.value ?: return emptyList()
        val adHoc = current.attribution as? Attribution.AdHoc ?: return emptyList()
        return goalCandidates(current, adHoc.activityTypeCode, adHoc.timeControlLabel, weekGoals)
    }

    fun change(to: Attribution, outgoingCount: Int?, outgoingCreditedGoalId: UUID?) {
        val resourceId = (to as? Attribution.Goal)?.let { goals[it.goalId]?.resource?.id }
        sessions.update { it.change(Instant.now(), to, resourceId, outgoingCount, outgoingCreditedGoalId) }
    }

    fun categorize(code: String) = sessions.update { it.categorize(code) }

    fun overrideDuration(minutes: Int) = sessions.update { it.overrideFinalDuration(minutes) }

    fun setGoalCredit(credit: PracticeSession.GoalCredit) = sessions.update { it.withGoalCredit(credit) }

    /** Attaching a resource can narrow "Counts toward" to that resource's goals. */
    fun setResource(id: UUID?) = sessions.update { it.withResource(id) }

    fun candidates(code: String, timeControlLabel: String?): List<WeeklyGoal> {
        val current = session.value ?: return emptyList()
        return goalCandidates(current, code, timeControlLabel, weekGoals)
    }

    fun save(code: String?, count: Int?, timeControlLabel: String?, topics: List<CoachingTopic>) {
        val current = session.value ?: return
        viewModelScope.launch {
            try {
                container.store.logPracticeSession(current, code, count, timeControlLabel, topics, container.preferences.firstLaunch())
                sessions.clear()
                events.send(SessionEvent.Finished())
            } catch (failure: Exception) {
                // Nothing was written: the transaction rolled back, and the session is still here.
                events.send(SessionEvent.SaveFailed)
            }
        }
    }

    fun discard() {
        sessions.clear()
        events.trySend(SessionEvent.Finished())
    }
}
