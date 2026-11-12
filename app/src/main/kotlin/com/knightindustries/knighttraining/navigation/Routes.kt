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
