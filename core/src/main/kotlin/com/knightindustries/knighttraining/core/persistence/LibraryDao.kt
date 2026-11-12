package com.knightindustries.knighttraining.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.knightindustries.knighttraining.core.persistence.entities.CatalogCourseEntity
import com.knightindustries.knighttraining.core.persistence.entities.ResourceCompletionEntity
import com.knightindustries.knighttraining.core.persistence.entities.ResourceEntity
import java.time.Instant
import java.util.UUID

/** The resource library and the course catalog. */
@Dao
interface LibraryDao {
    @Query("SELECT * FROM resource ORDER BY title COLLATE NOCASE")
    suspend fun allResources(): List<ResourceEntity>

    @Query("SELECT * FROM resource WHERE title LIKE :pattern OR author LIKE :pattern OR notes LIKE :pattern ORDER BY title COLLATE NOCASE")
    suspend fun searchResources(pattern: String): List<ResourceEntity>

    /** The deliberate "I finished this": 100%, a completion counted, recency refreshed. */
    @Query("UPDATE resource SET progressPercentage = 100, completionCount = completionCount + 1, lastAccessedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun finish(id: UUID, now: Instant)

    /** Back to 0% for another pass; completions and their count stay. */
    @Query("UPDATE resource SET progressPercentage = 0, lastAccessedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun restart(id: UUID, now: Instant)

    @Query("UPDATE resource SET completionCount = completionCount + 1, updatedAt = :now WHERE id = :id")
    suspend fun countCompletion(id: UUID, now: Instant)

    @Query("DELETE FROM resource WHERE id = :id")
    suspend fun deleteResource(id: UUID)

    @Query("SELECT COUNT(*) FROM resource WHERE progressPercentage = 100")
    suspend fun finishedCount(): Int

    @Query("SELECT catalogCourseId FROM resource WHERE catalogCourseId IS NOT NULL")
    suspend fun importedCatalogCourseIds(): List<UUID>

    /** Completed seconds logged against a resource in `[start, end)`, by start time. */
    @Query(
        """
        SELECT COALESCE(SUM(duration), 0) FROM activity
        WHERE resourceId = :id AND endedAt IS NOT NULL AND startedAt >= :start AND startedAt < :end
        """,
    )
    suspend fun trackedSeconds(id: UUID, start: Instant, end: Instant): Long

    // MARK: - Completions

    @Insert
    suspend fun insertCompletion(completion: ResourceCompletionEntity)

    @Query("SELECT * FROM resource_completion WHERE resourceId = :resourceId ORDER BY completedAt DESC")
    suspend fun completions(resourceId: UUID): List<ResourceCompletionEntity>

    // MARK: - Catalog

    /**
     * Keyed on (platform, externalId), and the conflict keeps the row's id. A resync that
     * replaced ids once silently cut every library course's `catalogCourseId` (SET NULL) on iOS.
     */
    @Query(
        """
        INSERT INTO catalog_course (id, platform, externalId, title, author, imageUrl, url, fetchedAt,
            courseDescription, topicCode, minRating, maxRating, lengthMinutes, languages)
        VALUES (:id, :platform, :externalId, :title, :author, :imageUrl, :url, :fetchedAt,
            :courseDescription, :topicCode, :minRating, :maxRating, :lengthMinutes, :languages)
        ON CONFLICT(platform, externalId) DO UPDATE SET
            title = excluded.title, author = excluded.author, imageUrl = excluded.imageUrl, url = excluded.url,
            fetchedAt = excluded.fetchedAt, courseDescription = excluded.courseDescription,
            topicCode = excluded.topicCode, minRating = excluded.minRating, maxRating = excluded.maxRating,
            lengthMinutes = excluded.lengthMinutes, languages = excluded.languages
        """,
    )
    suspend fun upsertCourse(
        id: UUID, platform: String, externalId: String, title: String, author: String?, imageUrl: String?, url: String?,
        fetchedAt: Instant, courseDescription: String?, topicCode: String?, minRating: Int?, maxRating: Int?,
        lengthMinutes: Int?, languages: String?,
    )

    @Query("SELECT * FROM catalog_course ORDER BY title COLLATE NOCASE")
    suspend fun allCourses(): List<CatalogCourseEntity>

    @Query("SELECT * FROM catalog_course WHERE id = :id")
    suspend fun course(id: UUID): CatalogCourseEntity?

    @Query("SELECT * FROM catalog_course WHERE platform = :platform AND externalId = :externalId")
    suspend fun course(platform: String, externalId: String): CatalogCourseEntity?
}
