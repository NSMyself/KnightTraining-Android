package com.knightindustries.knighttraining.core.model

import java.time.Instant
import java.util.UUID

/** Something the user studies from. */
data class Resource(
    val id: UUID = UUID.randomUUID(),
    val kind: ResourceKind,
    val title: String,
    val author: String? = null,
    val url: String? = null,
    val imageUrl: String? = null,
    val description: String? = null,
    val isOwned: Boolean = false,
    val isAutoImported: Boolean = false,
    /** 0–100. */
    val progressPercentage: Int = 0,
    val completionCount: Int = 0,
    val catalogCourseId: UUID? = null,
    val notes: String? = null,
    val edition: String? = null,
    val publisher: String? = null,
    val publicationDate: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    /** Drives the widget's "continue" line. Only engagement bumps it, never metadata edits. */
    val lastAccessedAt: Instant? = Instant.now(),
) {
    val isInProgress: Boolean get() = progressPercentage in 1..99
    val isCompleted: Boolean get() = progressPercentage == 100
    val isFromCatalog: Boolean get() = catalogCourseId != null

    /**
     * The deliberate "finish" path: clamps to 0–100 and counts a completion at 100. Freely
     * draggable controls must not use this (iOS `setResourceProgress`): oscillating around 100
     * would count a completion each time.
     */
    fun withProgress(percentage: Int, now: Instant = Instant.now()): Resource {
        val clamped = percentage.coerceIn(0, 100)
        return copy(
            progressPercentage = clamped,
            completionCount = if (clamped == 100) completionCount + 1 else completionCount,
            updatedAt = now,
            lastAccessedAt = now,
        )
    }

    /** A metadata edit: keeps [lastAccessedAt]. */
    fun withOwnership(owned: Boolean, now: Instant = Instant.now()): Resource =
        copy(isOwned = owned, updatedAt = now)

    /** A metadata edit: keeps [lastAccessedAt]. */
    fun withNotes(notes: String?, now: Instant = Instant.now()): Resource =
        copy(notes = notes, updatedAt = now)
}

data class ResourceCompletion(
    val id: UUID = UUID.randomUUID(),
    val resourceId: UUID,
    val completedAt: Instant = Instant.now(),
    val notes: String? = null,
    val rating: Int? = null,
    val durationDays: Int? = null,
)
