package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.CoachingTopic
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.TimeControl
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.persistence.Lookups
import com.knightindustries.knighttraining.core.persistence.entities.GoalTemplateEntity
import com.knightindustries.knighttraining.core.persistence.entities.PlanTemplateEntity
import com.knightindustries.knighttraining.core.session.PracticeSession
import com.knightindustries.knighttraining.core.session.PracticeSession.Attribution
import java.time.Instant
import java.util.UUID

/** A block as the plan builder hands it over. */
data class NewBlock(
    val name: String?,
    val activityType: ActivityType,
    val targetType: TargetType,
    val targetValue: Int,
    val timeControlLabel: String? = null,
    val resource: Resource? = null,
)

/**
 * Saves a new plan and its blocks and makes it the active one, in one transaction (iOS writes
 * these separately). A blank [name] is stored as the first free "Untitled training plan" variant,
 * whose words the app supplies. With [commit], it's then committed to the current effective week.
 */
suspend fun TrainingStore.createPlan(
    name: String?,
    blocks: List<NewBlock>,
    untitledBase: String,
    untitledNumbered: (Int) -> String,
    commit: Boolean = true,
    firstLaunch: Instant? = null,
    calendar: WeekCalendar = calendar(),
): PlanTemplate {
    require(blocks.isNotEmpty()) { "A plan needs at least one training block" }
    val at = now()
    val id = UUID.randomUUID()

    write {
        val resolvedName = name?.trim()?.takeIf { it.isNotEmpty() }
            ?: PlanTemplate.untitledName(planDao.visibleTemplateNames(null), untitledBase, untitledNumbered)
        planDao.upsertTemplate(PlanTemplateEntity(id, resolvedName, isActive = false, createdAt = at, updatedAt = at))
        planDao.deactivateAll(at)
        planDao.activate(id, at)
        for (block in blocks) {
            planDao.insertGoalTemplate(
                GoalTemplateEntity(
                    id = UUID.randomUUID(), planTemplateId = id, name = block.name,
                    activityTypeId = block.activityType.id, resourceId = block.resource?.id,
                    targetType = block.targetType.code, targetValue = block.targetValue, createdAt = at,
                    timeControlLabel = block.timeControlLabel.takeIf { block.activityType.code == ActivityType.PLAYING },
                ),
            )
        }
    }
    emit(ActivePlanTemplateChanged(id))
    if (commit) createWeeklyPlan(id, firstLaunch, calendar)
    return planTemplate(id)!!
}

// MARK: - Practice sessions

/**
 * The goals of the week a session *started* in (first-week grace included), which is the week it
 * counts toward even when saved after midnight.
 */
suspend fun TrainingStore.sessionWeekGoals(session: PracticeSession, firstLaunch: Instant?, calendar: WeekCalendar = calendar()): List<WeeklyGoal> =
    weeklyPlanContaining(session.startedAt, firstLaunch, calendar)?.weeklyGoals.orEmpty()

/** The goals a session without a plan goal could count toward: the review's "Counts toward". */
fun goalCandidates(session: PracticeSession, activityTypeCode: String, timeControlLabel: String?, weekGoals: List<WeeklyGoal>): List<WeeklyGoal> {
    if (session.attribution is Attribution.Goal) return emptyList()
    return WeeklyGoal.creditCandidates(
        activityTypeCode, session.resourceId, timeControlLabel?.let(TimeControl::parse)?.category, weekGoals,
    )
}

/**
 * Saves a stopped session: every earlier segment and the final one, each as its own activity, in
 * one [logSession] transaction.
 *
 * - A plan goal's segment moves that goal by its own measure (seconds or count; a progress goal
 *   gets the link but no delta, as on iOS).
 * - An ad-hoc segment moves the goal picked when switching away from it.
 * - The final segment, when it has no plan goal, moves what the review's "Counts toward" resolves
 *   to. Skipping the category ([finalActivityTypeCode] `null`) logs it as general, crediting nothing.
 *
 * Time controls are kept for games only and topics for lessons only.
 */
suspend fun TrainingStore.logPracticeSession(
    session: PracticeSession,
    finalActivityTypeCode: String?,
    count: Int? = null,
    timeControlLabel: String? = null,
    coachingTopics: List<CoachingTopic> = emptyList(),
    firstLaunch: Instant? = null,
    calendar: WeekCalendar = calendar(),
): List<UUID> {
    val end = session.stoppedAt ?: now()
    val resources = resourcesById(session.completedSegments.map { it.resourceId } + session.resourceId)
    val general = Lookups.activityType(ActivityType.GENERAL)!!

    fun activity(code: String?, resourceId: UUID?, start: Instant, stop: Instant, duration: Int, count: Int?, timeControl: String?, topics: List<CoachingTopic>): Activity {
        val type = code?.let { Lookups.activityType(it) } ?: general
        return Activity(
            activityType = type, resource = resourceId?.let(resources::get), startedAt = start, endedAt = stop,
            duration = duration, count = count, sessionId = session.sessionId,
            timeControlLabel = timeControl.takeIf { type.code == ActivityType.PLAYING },
            coachingTopics = if (type.code == ActivityType.COACHING) topics else emptyList(),
        )
    }

    fun planGoalProgress(attribution: Attribution.Goal, duration: Int, count: Int?) = SessionSegmentLog.GoalProgress(
        attribution.goalId,
        when (attribution.targetType) {
            TargetType.DURATION -> duration
            TargetType.COUNT -> count ?: 0
            TargetType.PROGRESS -> 0
        },
    )

    fun creditProgress(goal: WeeklyGoal?, duration: Int, count: Int?) =
        goal?.creditDelta(duration, count)?.let { SessionSegmentLog.GoalProgress(goal.id, it) }

    val segments = buildList {
        for (segment in session.completedSegments) {
            val attribution = segment.attribution
            val progress = when (attribution) {
                is Attribution.Goal -> planGoalProgress(attribution, segment.durationSeconds, segment.count)
                // A goal deleted since then just credits nothing.
                is Attribution.AdHoc -> creditProgress(segment.creditedGoalId?.let { weeklyGoal(it) }, segment.durationSeconds, segment.count)
                Attribution.General -> null
            }
            add(
                SessionSegmentLog(
                    activity(attribution.activityTypeCode, segment.resourceId, segment.start, segment.end, segment.durationSeconds, segment.count, attribution.timeControl, emptyList()),
                    progress,
                ),
            )
        }

        val duration = session.finalSegmentSeconds(end)
        val attribution = session.attribution
        val code = if (attribution is Attribution.Goal) attribution.activityTypeCode else finalActivityTypeCode
        val progress = when {
            attribution is Attribution.Goal -> planGoalProgress(attribution, duration, count)
            finalActivityTypeCode == null -> null
            else -> {
                val candidates = goalCandidates(session, finalActivityTypeCode, timeControlLabel, sessionWeekGoals(session, firstLaunch, calendar))
                creditProgress(session.goalCredit.resolve(candidates), duration, count)
            }
        }
        add(SessionSegmentLog(activity(code, session.resourceId, session.segmentStart, end, duration, count, timeControlLabel, coachingTopics), progress))
    }

    return logSession(segments)
}

/** This week's goals still open, plan order: what "Start session" offers. */
fun WeeklyPlan.openGoals(): List<WeeklyGoal> = weeklyGoals.filterNot { it.isCompleted }
