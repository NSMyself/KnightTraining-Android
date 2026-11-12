package com.knightindustries.knighttraining.core.model

import java.net.URI
import java.time.Instant
import java.util.UUID

/**
 * A course from the read-only catalog (ChessMood / Chessable), for discovery. Adding one to the
 * library copies it into a [Resource], which protects the user if the course is later delisted.
 */
data class CatalogCourse(
    val id: UUID = UUID.randomUUID(),
    val platform: String,
    val externalId: String,
    val title: String,
    val author: String? = null,
    val imageUrl: String? = null,
    val url: String? = null,
    val courseDescription: String? = null,
    /** The activity code it trains: openings, endgames or puzzles only. */
    val topicCode: String? = null,
    val minRating: Int? = null,
    val maxRating: Int? = null,
    val lengthMinutes: Int? = null,
    /** ISO 639-1 codes, original language first. Chessable only. */
    val languages: List<String> = emptyList(),
    val fetchedAt: Instant = Instant.now(),
) {
    val isChessmood: Boolean get() = platform == CHESSMOOD

    /**
     * This course's page with the referral tag (replacing any other), or the site-wide referral
     * link. `null` for anything but ChessMood: there's no other agreement.
     */
    val referralUrl: String?
        get() {
            if (!isChessmood) return null
            val page = url?.let { runCatching { URI(it) }.getOrNull() } ?: return CHESSMOOD_REFERRAL_URL
            val kept = page.rawQuery?.split("&")?.filter { it.isNotEmpty() && !it.startsWith("r=") }.orEmpty()
            val query = (kept + "r=KnightTraining").joinToString("&")
            return URI(page.scheme, page.rawAuthority, page.rawPath, null, null).toString() + "?" + query
        }

    fun toResource(kind: ResourceKind, now: Instant = Instant.now()) = Resource(
        kind = kind, title = title, author = author, url = url, imageUrl = imageUrl, description = courseDescription,
        catalogCourseId = id, createdAt = now, updatedAt = now,
    )

    /** Where a course lands in the catalog browser. */
    sealed interface LanguageSection {
        /** The app's language (a base code like `es`), never English. */
        data class Preferred(val code: String) : LanguageSection

        data object English : LanguageSection

        /** Anything else, and courses with no language data. */
        data object Other : LanguageSection
    }

    companion object {
        const val CHESSMOOD = "chessmood"
        const val CHESSABLE = "chessable"

        /** The maintainer's affiliate link: referred users get 20% off. */
        const val CHESSMOOD_REFERRAL_URL = "https://chessmood.com/?r=KnightTraining"

        val suggestableTopicCodes = setOf(ActivityType.OPENINGS, ActivityType.ENDGAMES, ActivityType.PUZZLES)

        /** The catalog's `topic` as an activity code; the catalog says `tactics` for puzzles. */
        fun topicCode(catalogTopic: String?): String? {
            val code = catalogTopic?.lowercase()?.let { if (it == "tactics") ActivityType.PUZZLES else it } ?: return null
            return code.takeIf { it in suggestableTopicCodes }
        }

        /**
         * The user's language first, then English, then the rest; a course offered in several
         * languages appears once, in the first section it qualifies for. Empty sections are left
         * out, and each keeps the incoming order.
         */
        fun languageSections(courses: List<CatalogCourse>, preferredLanguage: String?): List<Pair<LanguageSection, List<CatalogCourse>>> {
            val preferred = preferredLanguage?.let(::baseCode)?.takeIf { it != "en" }
            val buckets = linkedMapOf<LanguageSection, MutableList<CatalogCourse>>()
            preferred?.let { buckets[LanguageSection.Preferred(it)] = mutableListOf() }
            buckets[LanguageSection.English] = mutableListOf()
            buckets[LanguageSection.Other] = mutableListOf()
            for (course in courses) {
                val codes = course.languages.map(::baseCode).toSet()
                val section = when {
                    preferred != null && preferred in codes -> LanguageSection.Preferred(preferred)
                    "en" in codes -> LanguageSection.English
                    else -> LanguageSection.Other
                }
                buckets.getValue(section) += course
            }
            return buckets.filterValues { it.isNotEmpty() }.map { (section, list) -> section to list.toList() }
        }

        private fun baseCode(identifier: String) = identifier.lowercase().takeWhile { it != '-' && it != '_' }
    }
}
