package com.knightindustries.knighttraining.core.model

import java.time.Instant
import java.util.UUID

/** "How I train." Evolves freely; changes apply to future weeks only. */
data class PlanTemplate(
    val id: UUID = UUID.randomUUID(),
    val name: String? = null,
    val isActive: Boolean = false,
    /**
     * "Deleted" as far as the user is concerned. Real deletion cascades away every week committed
     * from this template, so "Delete Plan" archives instead.
     */
    val isArchived: Boolean = false,
    val goalTemplates: List<GoalTemplate> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    val goalCount: Int get() = goalTemplates.size
    val hasGoals: Boolean get() = goalTemplates.isNotEmpty()

    /** [name], or [fallback] (the translated "My Training Plan") when it's blank. */
    fun displayName(fallback: String): String = displayName(name, fallback)

    companion object {
        fun displayName(name: String?, fallback: String): String =
            if (name.isNullOrBlank()) fallback else name

        /**
         * The name a plan saved without one is stored under: [base] ("Untitled training plan"),
         * or the first free [numbered] variant ("Untitled training plan (n)"), the way macOS
         * numbers duplicate files. Stored rather than derived, so it stays in the language it was
         * saved in. Both strings are translations the app supplies.
         */
        fun untitledName(avoiding: List<String?>, base: String, numbered: (Int) -> String): String {
            val taken = avoiding.mapNotNull { it?.trim() }.toSet()
            if (base !in taken) return base
            return generateSequence(1) { it + 1 }.map(numbered).first { it !in taken }
        }
    }
}

/** A recurring goal within a plan template. */
data class GoalTemplate(
    val id: UUID = UUID.randomUUID(),
    val planTemplateId: UUID,
    val name: String? = null,
    val activityType: ActivityType,
    val resource: Resource? = null,
    val targetType: TargetType,
    /** Seconds for [TargetType.DURATION], items for [TargetType.COUNT], percent for progress. */
    val targetValue: Int,
    /** A [TimeControl.label]. Playing only; seeds the logging screen's default. */
    val timeControlLabel: String? = null,
    val createdAt: Instant = Instant.now(),
) {
    val timeControl: TimeControl?
        get() = timeControlLabel?.let(TimeControl::parse)

    val hasResource: Boolean get() = resource != null
}
