package com.knightindustries.knighttraining.core.net

import com.knightindustries.knighttraining.core.model.CatalogCourse
import com.knightindustries.knighttraining.core.store.TrainingStore
import com.knightindustries.knighttraining.core.store.hasCatalogCourses
import com.knightindustries.knighttraining.core.store.saveCatalogCourses
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * The read-only course catalog: a JSON file in GitHub, served through jsDelivr (iOS
 * `CourseCatalogService` + `CatalogSyncService`). Re-fetched with the last ETag, so an unchanged
 * catalog is a cheap 304. Any failure keeps the catalog already stored.
 */
class CatalogSync(
    private val store: TrainingStore,
    private val etags: EtagStore,
    private val http: HttpGet = OkHttpGet(),
    private val now: () -> Instant = Instant::now,
) {
    /** Where the last ETag lives: the app keeps it in DataStore. */
    interface EtagStore {
        suspend fun get(): String?

        suspend fun set(etag: String)
    }

    /** `true` when a new catalog was stored. */
    suspend fun syncIfNeeded(): Boolean {
        // The ETag lives in DataStore and the courses in the database, so the two can part (iOS hit
        // it when a debug schema reset wiped the table and kept the ETag: a 304 forever, for a
        // catalog that was gone). With nothing stored, always ask for the full catalog.
        val etag = if (store.hasCatalogCourses()) etags.get() else null
        val response = http.get(CATALOG_URL, etag?.let { mapOf("If-None-Match" to it) }.orEmpty()) ?: return false
        if (response.status != 200) return false
        val newEtag = response.headers["etag"] ?: return false
        val courses = parse(response.body, now()) ?: return false
        store.saveCatalogCourses(courses)
        etags.set(newEtag)
        return true
    }

    @Serializable
    private data class Payload(val chessable: List<Chessable> = emptyList(), val chessmood: List<Chessmood> = emptyList())

    @Serializable
    private data class Chessable(val id: String, val title: String, val author: String? = null, val languages: List<String>? = null)

    @Serializable
    private data class Chessmood(
        val id: String,
        val title: String,
        @SerialName("image_url") val imageUrl: String? = null,
        val url: String? = null,
        val description: String? = null,
        val topic: String? = null,
        @SerialName("min_rating") val minRating: Int? = null,
        @SerialName("max_rating") val maxRating: Int? = null,
        @SerialName("duration_minutes") val durationMinutes: Int? = null,
    )

    companion object {
        const val CATALOG_URL = "https://cdn.jsdelivr.net/gh/NSMyself/chesscourses@main/courses.json"

        private val json = Json { ignoreUnknownKeys = true }

        /** `null` when the payload doesn't decode: a wrong type anywhere keeps the old catalog. */
        fun parse(body: String, fetchedAt: Instant): List<CatalogCourse>? {
            val payload = runCatching { json.decodeFromString(Payload.serializer(), body) }.getOrNull() ?: return null
            return payload.chessable.map {
                CatalogCourse(platform = CatalogCourse.CHESSABLE, externalId = it.id, title = it.title, author = it.author, languages = it.languages.orEmpty(), fetchedAt = fetchedAt)
            } + payload.chessmood.map {
                CatalogCourse(
                    platform = CatalogCourse.CHESSMOOD, externalId = it.id, title = it.title, imageUrl = it.imageUrl, url = it.url,
                    courseDescription = it.description, topicCode = CatalogCourse.topicCode(it.topic), minRating = it.minRating,
                    maxRating = it.maxRating, lengthMinutes = it.durationMinutes, fetchedAt = fetchedAt,
                )
            }
        }
    }
}
