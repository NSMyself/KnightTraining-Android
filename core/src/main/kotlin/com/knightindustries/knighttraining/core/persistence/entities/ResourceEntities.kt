package com.knightindustries.knighttraining.core.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/** Read-only courses from the catalog JSON (ChessMood / Chessable). */
@Entity(
    tableName = "catalog_course",
    indices = [
        Index(value = ["platform", "externalId"], unique = true),
        Index(value = ["platform"]),
    ],
)
data class CatalogCourseEntity(
    @PrimaryKey val id: UUID,
    val platform: String,
    val externalId: String,
    val title: String,
    val author: String?,
    val imageUrl: String?,
    val url: String?,
    val fetchedAt: Instant,
    val courseDescription: String?,
    val topicCode: String?,
    val minRating: Int?,
    val maxRating: Int?,
    val lengthMinutes: Int?,
    /** Comma-separated ISO 639-1 codes. Chessable only. */
    val languages: String?,
)

/** What the user is actually studying. */
@Entity(
    tableName = "resource",
    foreignKeys = [
        ForeignKey(
            entity = ResourceKindEntity::class,
            parentColumns = ["id"],
            childColumns = ["kindId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CatalogCourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["catalogCourseId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["kindId"]),
        Index(value = ["progressPercentage", "lastAccessedAt"]),
        Index(value = ["catalogCourseId"]),
    ],
)
data class ResourceEntity(
    @PrimaryKey val id: UUID,
    val kindId: UUID,
    val title: String,
    val author: String?,
    val url: String?,
    val description: String?,
    val imageUrl: String?,
    @ColumnInfo(defaultValue = "0") val isOwned: Boolean = false,
    /** 0–100. */
    @ColumnInfo(defaultValue = "0") val progressPercentage: Int = 0,
    @ColumnInfo(defaultValue = "0") val completionCount: Int = 0,
    val catalogCourseId: UUID?,
    val notes: String?,
    val edition: String?,
    val publisher: String?,
    val publicationDate: Instant?,
    val lastAccessedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Created by the Lichess studies sync rather than by the user. */
    @ColumnInfo(defaultValue = "0") val isAutoImported: Boolean = false,
)

/** One row per time a resource was finished; a book can be finished more than once. */
@Entity(
    tableName = "resource_completion",
    foreignKeys = [
        ForeignKey(
            entity = ResourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["resourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["resourceId"])],
)
data class ResourceCompletionEntity(
    @PrimaryKey val id: UUID,
    val resourceId: UUID,
    val completedAt: Instant,
    val notes: String?,
    val rating: Int?,
    val durationDays: Int?,
)

/** A Chessable/ChessMood course added through sharing, remembered by platform identity. */
@Entity(
    tableName = "shared_course",
    foreignKeys = [
        ForeignKey(
            entity = ResourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["resourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["resourceId"], unique = true),
        Index(value = ["platform", "externalId"], unique = true),
        Index(value = ["platform", "slug"]),
    ],
)
data class SharedCourseEntity(
    @PrimaryKey val id: UUID,
    val resourceId: UUID,
    val platform: String,
    val externalId: String?,
    val slug: String?,
    val url: String,
    /** The title at share time. */
    val title: String,
    val createdAt: Instant,
)
