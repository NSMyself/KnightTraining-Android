package com.knightindustries.knighttraining.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Every destination. Serializable so the back stack survives process death. */
sealed interface Route : NavKey

/** The four bottom-bar destinations, mirroring iOS's tabs. */
@Serializable
sealed interface TabRoute : Route

@Serializable
data object HomeRoute : TabRoute

@Serializable
data object TrainingRoute : TabRoute

@Serializable
data object ResourcesRoute : TabRoute

@Serializable
data object ProfileRoute : TabRoute

/** The plan builder. [commit] also commits the new plan to this week. */
@Serializable
data class CreatePlanRoute(val commit: Boolean = true) : Route

/** Pick which saved plan this week commits to. */
@Serializable
data object PlanThisWeekRoute : Route

/** The practice timer and its review: the session in `SessionRepository`. */
@Serializable
data object SessionRoute : Route

/** One committed week's log. [weekStart] is ISO (`2026-11-08`). */
@Serializable
data class WeekLogRoute(val weekStart: String) : Route

/** The reflection on one committed week. [weeklyPlanId] is a UUID string. */
@Serializable
data class WeeklyReviewRoute(val weeklyPlanId: String) : Route

/** One resource. [resourceId] is a UUID string. */
@Serializable
data class ResourceDetailRoute(val resourceId: String) : Route

/** Every resource of one kind: the shelf for books and courses, the full list for studies. */
@Serializable
data class ResourceKindRoute(val kindCode: String) : Route

/** Edit a saved plan. [templateId] is a UUID string. */
@Serializable
data class EditPlanRoute(val templateId: String) : Route

/** Planned against actual, week by week, and the daily grid (iOS `TrainingHistoryScreen`). */
@Serializable
data object TrainingHistoryRoute : Route
