package com.knightindustries.knighttraining.core.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.time.Instant

data class BookLookup(
    val title: String? = null,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val publishedDate: Instant? = null,
    val description: String? = null,
    val imageUrl: String? = null,
)

/**
 * Authoritative book details by ISBN (iOS `BookLookupService`): Google Books first (free, no key),
 * with Open Library filling whatever Google leaves out, or everything when Google doesn't answer
 * (its key-less quota is shared worldwide). Google still wins where both answer: Open Library's
 * titles can be oddly cased. The ISBN is only a lookup key, never stored.
 */
class BookLookupService(private val http: HttpGet = OkHttpGet()) {
    suspend fun lookup(isbn: String): BookLookup? {
        val cleaned = isbn.filter { it.isDigit() || it == 'X' || it == 'x' }
        if (cleaned.isEmpty()) return null
        val google = http.get("https://www.googleapis.com/books/v1/volumes?q=isbn:$cleaned", emptyMap())
            ?.takeIf { it.isSuccess }?.let { parseGoogle(it.body) }
        if (google != null && google.title != null && google.authors.isNotEmpty() && google.imageUrl != null) return google
        val openLibrary = http.get("https://openlibrary.org/api/books?bibkeys=ISBN:$cleaned&jscmd=data&format=json", emptyMap())
            ?.takeIf { it.isSuccess }?.let { parseOpenLibrary(it.body, "ISBN:$cleaned") }
            ?: return google
        return merge(google, openLibrary)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Field by field: Google's value wherever it has one. */
        fun merge(google: BookLookup?, openLibrary: BookLookup): BookLookup = google?.let {
            BookLookup(
                title = it.title ?: openLibrary.title,
                authors = it.authors.ifEmpty { openLibrary.authors },
                publisher = it.publisher ?: openLibrary.publisher,
                publishedDate = it.publishedDate ?: openLibrary.publishedDate,
                description = it.description ?: openLibrary.description,
                imageUrl = it.imageUrl ?: openLibrary.imageUrl,
            )
        } ?: openLibrary

        fun parseGoogle(body: String): BookLookup? {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
            val info = ((root["items"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("volumeInfo") as? JsonObject ?: return null
            val links = info["imageLinks"] as? JsonObject
            val thumbnail = (links?.string("thumbnail") ?: links?.string("smallThumbnail"))?.replace("http://", "https://")
            return BookLookup(
                title = info.string("title"),
                authors = (info["authors"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
                publisher = info.string("publisher"),
                publishedDate = info.string("publishedDate")?.let(LinkMetadataService::parseFlexibleDate),
                description = info.string("description"),
                imageUrl = thumbnail,
            )
        }

        /** No description: this endpoint rarely has one. */
        fun parseOpenLibrary(body: String, key: String): BookLookup? {
            val record = runCatching { json.parseToJsonElement(body).jsonObject[key] as? JsonObject }.getOrNull() ?: return null
            val cover = record["cover"] as? JsonObject
            return BookLookup(
                title = record.string("title"),
                authors = (record["authors"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.string("name") }.orEmpty(),
                publisher = ((record["publishers"] as? JsonArray)?.firstOrNull() as? JsonObject)?.string("name"),
                publishedDate = record.string("publish_date")?.let(LinkMetadataService::parseFlexibleDate),
                imageUrl = cover?.string("large") ?: cover?.string("medium"),
            )
        }

        private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    }
}
