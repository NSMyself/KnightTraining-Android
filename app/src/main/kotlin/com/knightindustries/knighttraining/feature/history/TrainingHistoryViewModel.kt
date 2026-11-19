package com.knightindustries.knighttraining.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.store.TimeOfDayBucket
import com.knightindustries.knighttraining.core.store.WeeklyEffort
import com.knightindustries.knighttraining.core.store.currentStreak
import com.knightindustries.knighttraining.core.store.dailyDurationTotals
import com.knightindustries.knighttraining.core.store.mostCommonStudyTimeOfDay
import com.knightindustries.knighttraining.core.store.mostTrainedActivityType
import com.knightindustries.knighttraining.core.store.totalDuration
import com.knightindustries.knighttraining.core.store.weeklyEffortHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class TrainingHistory(
    val weeks: List<WeeklyEffort>,
    val streak: Int,
    val dailySeconds: Map<LocalDate, Int>,
    val today: LocalDate,
    val totalSeconds: Int,
    val mostTrained: ActivityType?,
    val usualTime: TimeOfDayBucket?,
) {
    /**
     * Two weeks with something in them, planned or trained: one point isn't a trend. Below that
     * the screen shows its empty state rather than a one-point "trend".
     */
    val hasTrend: Boolean get() = weeks.count { it.plannedSeconds > 0 || it.actualSeconds > 0 } >= 2

    /**
     * How often past weeks with a time plan matched or beat it, 0–100; `null` when none had one.
     * The current week is left out: it hasn't had its chance yet.
     */
    val compliancePercent: Int?
        get() {
            val past = weeks.dropLast(1).filter { it.plannedSeconds > 0 }
            if (past.isEmpty()) return null
            return Math.round(past.count { it.actualSeconds >= it.plannedSeconds } * 100.0 / past.size).toInt()
        }

    companion object {
        /** iOS's chart shows five weeks; its grid, 4 rows of 9 days. */
        const val WEEKS = 5
        const val GRID_ROWS = 4
        const val GRID_COLUMNS = 9
        const val GRID_DAYS = GRID_ROWS * GRID_COLUMNS
    }
}

/** Training History's numbers, read once on opening (iOS reads them in its `.task`). */
class TrainingHistoryViewModel(private val container: AppContainer) : ViewModel() {
    private val _history = MutableStateFlow<TrainingHistory?>(null)
    val history: StateFlow<TrainingHistory?> = _history.asStateFlow()

    init {
        viewModelScope.launch {
            val store = container.store
            _history.value = TrainingHistory(
                weeks = store.weeklyEffortHistory(weeks = TrainingHistory.WEEKS),
                streak = store.currentStreak(),
                dailySeconds = store.dailyDurationTotals(days = TrainingHistory.GRID_DAYS),
                today = WeekCalendar.system().localDate(Instant.now()),
                totalSeconds = store.totalDuration(),
                mostTrained = store.mostTrainedActivityType(),
                usualTime = store.mostCommonStudyTimeOfDay(),
            )
        }
    }
}
