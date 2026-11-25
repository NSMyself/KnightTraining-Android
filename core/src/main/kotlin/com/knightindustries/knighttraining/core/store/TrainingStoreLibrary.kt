package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.CatalogCourse
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.ResourceCompletion
import com.knightindustries.knighttraining.core.model.ResourceKind
import com.knightindustries.knighttraining.core.persistence.entities.CatalogCourseEntity
import com.knightindustries.knighttraining.core.persistence.entities.ResourceCompletionEntity
import com.knightindustries.knighttraining.core.persistence.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

private fun CatalogCourseEntity.toDomain() = CatalogCourse(
    id, platform, externalId, title, author, imageUrl, url, courseDescription, topicCode, minRating, maxRating,
    lengthMinutes, languages?.split(",")?.filter { it.isNotBlank() }.orEmpty(), fetchedAt,
)

private fun ResourceCompletionEntity.toDomain() = ResourceCompletion(id, resourceId, completedAt, notes, rating, durationDays)

suspend fun TrainingStore.allResources(): List<Resource> = db.libraryDao().allResources().map { it.toDomain() }

/**
 * The library, live: a background writer (the Lichess studies sync, a share import) reaches a
 * library screen that's already open.
 */
fun TrainingStore.observeAllResources(): Flow<List<Resource>> =
    db.invalidationTracker.createFlow("resource").map { allResources() }.distinctUntilChanged()

fun TrainingStore.observeResource(id: UUID): Flow<Resource?> =
    db.invalidationTracker.createFlow("resource").map { resource(id) }.distinctUntilChanged()

suspend fun TrainingStore.searchResources(query: String): List<Resource> =
    db.libraryDao().searchResources("%$query%").map { it.toDomain() }

suspend fun TrainingStore.deleteResource(id: UUID) = db.libraryDao().deleteResource(id)

/**
 * "Mark as finished": 100%, one more completion counted, and a completion row with the user's
 * notes and rating, in one transaction. A dragged slider reaching 100% uses
 * [setResourceProgress] instead and never counts a completion.
 */
suspend fun TrainingStore.markResourceFinished(id: UUID, notes: String? = null, rating: Int? = null) {
    val at = now()
    write {
        db.libraryDao().finish(id, at)
        db.libraryDao().insertCompletion(ResourceCompletionEntity(UUID.randomUUID(), id, at, notes, rating, null))
    }
}

/** A completion recorded without touching progress (iOS `recordCompletion`). */
suspend fun TrainingStore.recordCompletion(id: UUID, notes: String? = null, rating: Int? = null, durationDays: Int? = null) {
    val at = now()
    write {
        db.libraryDao().insertCompletion(ResourceCompletionEntity(UUID.randomUUID(), id, at, notes, rating, durationDays))
        db.libraryDao().countCompletion(id, at)
    }
}

/** Another pass: progress back to 0, completions kept. */
suspend fun TrainingStore.restartResource(id: UUID) = db.libraryDao().restart(id, now())

/** Newest first. */
suspend fun TrainingStore.completions(resourceId: UUID): List<ResourceCompletion> =
    db.libraryDao().completions(resourceId).map { it.toDomain() }

/** "Time invested": every completed session logged against the resource. */
suspend fun TrainingStore.timeInvested(resourceId: UUID, start: Instant = Instant.EPOCH, end: Instant? = null): Int =
    db.libraryDao().trackedSeconds(resourceId, start, end ?: Instant.ofEpochSecond(FAR_FUTURE_SECONDS)).toInt()

/** Year 9999: an open upper bound SQLite compares like any other instant. */
private const val FAR_FUTURE_SECONDS = 253_402_300_799L

/** Matches `Resource.isCompleted`, the definition every "Completed" surface uses. */
suspend fun TrainingStore.finishedResourceCount(): Int = db.libraryDao().finishedCount()

// MARK: - Catalog

/** Whether any catalog course is stored: [com.knightindustries.knighttraining.core.net.CatalogSync] refetches in full when not. */
suspend fun TrainingStore.hasCatalogCourses(): Boolean = db.libraryDao().hasCourses()

/** Saves a synced catalog in one transaction; each course keeps its id across resyncs. */
suspend fun TrainingStore.saveCatalogCourses(courses: List<CatalogCourse>) = write {
    courses.forEach { course ->
        db.libraryDao().upsertCourse(
            course.id, course.platform, course.externalId, course.title, course.author, course.imageUrl, course.url,
            course.fetchedAt, course.courseDescription, course.topicCode, course.minRating, course.maxRating,
            course.lengthMinutes, course.languages.takeIf { it.isNotEmpty() }?.joinToString(","),
        )
    }
}

suspend fun TrainingStore.catalogCourses(): List<CatalogCourse> = db.libraryDao().allCourses().map { it.toDomain() }

suspend fun TrainingStore.catalogCourse(id: UUID): CatalogCourse? = db.libraryDao().course(id)?.toDomain()

suspend fun TrainingStore.catalogCourse(platform: String, externalId: String): CatalogCourse? =
    db.libraryDao().course(platform, externalId)?.toDomain()

/** Courses already in the library, so the browser offers only what can still be added. */
suspend fun TrainingStore.importedCatalogCourseIds(): Set<UUID> = db.libraryDao().importedCatalogCourseIds().toSet()

/** Copies a catalog course into the library as a course. */
suspend fun TrainingStore.addCatalogCourse(course: CatalogCourse): Resource {
    val resource = course.toResource(resourceKind(ResourceKind.COURSE)!!, now())
    saveResource(resource)
    return resource
}
