package com.knightindustries.knighttraining.core.store

import androidx.room.deferredTransaction
import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.ResourceKind
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.persistence.AppDatabase
import com.knightindustries.knighttraining.core.persistence.Lookups
import com.knightindustries.knighttraining.core.persistence.entities.WeeklyPlanEntity
import com.knightindustries.knighttraining.core.persistence.toDomain
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Emitted once, when a week's last outstanding (non-bonus) goal completes. */
data class WeeklyPlanCompleted(val weekStart: LocalDate, val streakLength: Int, val goalCount: Int)

/** Emitted whenever the active plan template changes. */
data class ActivePlanTemplateChanged(val templateId: UUID)

/**
 * The domain-level face of the database: the counterpart of iOS's `AppDatabase` extensions.
 * Converts entities to domain models and owns every multi-step write, each in one transaction.
 *
 * Split across files by area (`TrainingStoreWeeks.kt`, `TrainingStoreSession.kt`, …) like iOS's
 * `AppDatabase+*.swift`.
 *
 * @param now the clock; tests pin it.
 * @param calendar resolved on every call, since the zone or locale can change while the app runs.
 */
class TrainingStore(
    val db: AppDatabase,
    internal val now: () -> Instant = Instant::now,
    internal val calendar: () -> WeekCalendar = WeekCalendar::system,
) {
    internal val activityDao = db.activityDao()
    internal val planDao = db.planDao()
    internal val weekDao = db.weekDao()
    internal val resourceDao = db.resourceDao()
    internal val accountDao = db.accountDao()

    // No replay, like iOS's `EventBroadcaster`: subscribe before triggering the work you expect to
    // emit. The buffer keeps a slow collector from blocking a write's caller.
    private val weeklyPlanCompletedFlow = MutableSharedFlow<WeeklyPlanCompleted>(extraBufferCapacity = 16)
    private val activePlanTemplateChangedFlow = MutableSharedFlow<ActivePlanTemplateChanged>(extraBufferCapacity = 16)

    val weeklyPlanCompletedEvents: SharedFlow<WeeklyPlanCompleted> = weeklyPlanCompletedFlow.asSharedFlow()
    val activePlanTemplateChangedEvents: SharedFlow<ActivePlanTemplateChanged> = activePlanTemplateChangedFlow.asSharedFlow()

    internal suspend fun emit(event: WeeklyPlanCompleted) = weeklyPlanCompletedFlow.emit(event)

    internal suspend fun emit(event: ActivePlanTemplateChanged) = activePlanTemplateChangedFlow.emit(event)

    /** One write transaction: every DAO call inside lands or none does. */
    internal suspend fun <T> write(block: suspend () -> T): T =
        db.useWriterConnection { connection -> connection.immediateTransaction { block() } }

    /** One read transaction, so a set of queries can't straddle a write and disagree. */
    internal suspend fun <T> read(block: suspend () -> T): T =
        db.useReaderConnection { connection -> connection.deferredTransaction { block() } }

    suspend fun seedPresetsIfNeeded() = db.lookupDao().seedPresetsIfNeeded()

    // MARK: - Lookups

    fun activityType(code: String): ActivityType? = Lookups.activityType(code)

    val allActivityTypes: List<ActivityType> get() = Lookups.activityTypes.values.toList()

    fun resourceKind(code: String): ResourceKind? = Lookups.resourceKind(code)

    // MARK: - Batch loading (no per-row queries)

    internal suspend fun resourcesById(ids: Collection<UUID?>): Map<UUID, Resource> {
        val wanted = ids.filterNotNull().distinct()
        if (wanted.isEmpty()) return emptyMap()
        return resourceDao.byIds(wanted).associate { it.id to it.toDomain() }
    }

    internal suspend fun withGoals(plans: List<WeeklyPlanEntity>): List<WeeklyPlan> {
        if (plans.isEmpty()) return emptyList()
        val goals = weekDao.goals(plans.map { it.id })
        val resources = resourcesById(goals.map { it.resourceId })
        val byPlan: Map<UUID, List<WeeklyGoal>> = goals.groupBy({ it.weeklyPlanId }, { it.toDomain(resources) })
        return plans.map { it.toDomain(byPlan[it.id].orEmpty()) }
    }

    internal suspend fun withGoals(plan: WeeklyPlanEntity?): WeeklyPlan? = plan?.let { withGoals(listOf(it)).single() }
}
