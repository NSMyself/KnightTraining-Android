package com.knightindustries.knighttraining.core.persistence

import com.knightindustries.knighttraining.core.persistence.entities.ActivityTypeEntity
import com.knightindustries.knighttraining.core.persistence.entities.ResourceKindEntity
import java.util.UUID

/**
 * The seeded lookup rows, with the same fixed ids and codes as iOS
 * (`Core/Sources/Core/Persistence/Persistence+Seeding.swift`). These are the app's chess
 * vocabulary: adding one means adding a glossary entry in every shipped language.
 */
object Presets {
    val activityTypes: List<ActivityTypeEntity> = listOf(
        ActivityTypeEntity(UUID.fromString("00000000-0000-0000-0000-000000000001"), "puzzles"),
        ActivityTypeEntity(UUID.fromString("00000000-0000-0000-0000-000000000002"), "playing"),
        ActivityTypeEntity(UUID.fromString("00000000-0000-0000-0000-000000000003"), "analyzing"),
        ActivityTypeEntity(UUID.fromString("00000000-0000-0000-0000-000000000006"), "openings"),
        ActivityTypeEntity(UUID.fromString("00000000-0000-0000-0000-000000000007"), "endgames"),
        ActivityTypeEntity(UUID.fromString("00000000-0000-0000-0000-000000000008"), "coaching"),
        // Fallback for uncategorized activities; never offered as a pickable category.
        ActivityTypeEntity(UUID.fromString("00000000-0000-0000-0000-000000000005"), "general"),
    )

    val resourceKinds: List<ResourceKindEntity> = listOf(
        ResourceKindEntity(UUID.fromString("00000000-0000-0000-0001-000000000001"), "book"),
        ResourceKindEntity(UUID.fromString("00000000-0000-0000-0001-000000000002"), "course"),
        ResourceKindEntity(UUID.fromString("00000000-0000-0000-0001-000000000003"), "video"),
        ResourceKindEntity(UUID.fromString("00000000-0000-0000-0001-000000000004"), "article"),
        ResourceKindEntity(UUID.fromString("00000000-0000-0000-0001-000000000005"), "lichess_study"),
    )
}
