package com.knightindustries.knighttraining.core.persistence.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Seeded, immutable lookup rows. Unlike iOS there's no `title` or `icon` column: display names
 * come from the code at display time (AGENTS.md §14 rule 1), and iOS's icons are SF Symbol names,
 * which mean nothing here. The app maps `code` to a Material icon instead.
 */
@Entity(tableName = "activity_type", indices = [Index(value = ["code"], unique = true)])
data class ActivityTypeEntity(
    @PrimaryKey val id: UUID,
    val code: String,
)

@Entity(tableName = "resource_kind", indices = [Index(value = ["code"], unique = true)])
data class ResourceKindEntity(
    @PrimaryKey val id: UUID,
    val code: String,
)
