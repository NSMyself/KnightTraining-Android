package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.Account
import com.knightindustries.knighttraining.core.model.Milestone
import com.knightindustries.knighttraining.core.model.MilestonesSnapshot
import com.knightindustries.knighttraining.core.persistence.toDomain
import com.knightindustries.knighttraining.core.persistence.toEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import java.util.UUID

suspend fun TrainingStore.milestonesSnapshot(): MilestonesSnapshot = read {
    val dao = db.lichessDao()
    val account = dao.account(Account.LICHESS)
    MilestonesSnapshot(
        milestones = dao.milestones().map { it.toDomain() },
        latestRatings = account?.let { dao.latestRatings(it.id).associate { r -> r.ratingType to r.rating } }.orEmpty(),
        currentLichessTitle = account?.title,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
fun TrainingStore.observeMilestonesSnapshot(): Flow<MilestonesSnapshot> =
    db.invalidationTracker.createFlow("milestone", "account", "elo_snapshot")
        .mapLatest { milestonesSnapshot() }
        .distinctUntilChanged()

/**
 * Saves, then reevaluates at once: a target already met (a rating already past it) completes on
 * the spot rather than at the next sync. Returns the milestones that just completed.
 */
suspend fun TrainingStore.saveMilestone(milestone: Milestone): List<Milestone> {
    db.lichessDao().upsertMilestone(milestone.toEntity())
    return reevaluateMilestones()
}

suspend fun TrainingStore.deleteMilestone(id: UUID) = db.lichessDao().deleteMilestone(id)

/** By hand: meant for free text, since Elo and title milestones complete themselves. */
suspend fun TrainingStore.completeMilestone(id: UUID) = db.lichessDao().setMilestoneCompletedAt(id, now())

suspend fun TrainingStore.reopenMilestone(id: UUID) = db.lichessDao().setMilestoneCompletedAt(id, null)

/**
 * The single auto-complete entry point: an Elo milestone completes when the latest rating of its
 * type meets the target, a title milestone when the Lichess account holds that title (or any, for
 * `null`). Free text never completes itself. Returns only what changed in this call, so a caller
 * can celebrate real transitions, not every no-op.
 */
suspend fun TrainingStore.reevaluateMilestones(): List<Milestone> = write {
    val dao = db.lichessDao()
    val account = dao.account(Account.LICHESS)
    val ratings = account?.let { dao.latestRatings(it.id).associate { r -> r.ratingType to r.rating } }.orEmpty()
    val title = account?.title
    val at = now()
    dao.milestones().map { it.toDomain() }.filter { !it.isCompleted }.mapNotNull { milestone ->
        val satisfied = when (milestone.kind) {
            Milestone.ELO -> {
                val current = milestone.ratingType?.let(ratings::get)
                current != null && milestone.targetRating != null && current >= milestone.targetRating
            }
            Milestone.TITLE -> title != null && (milestone.targetTitle == null || milestone.targetTitle == title)
            else -> false
        }
        if (!satisfied) return@mapNotNull null
        dao.setMilestoneCompletedAt(milestone.id, at)
        milestone.copy(completedAt = at)
    }
}
