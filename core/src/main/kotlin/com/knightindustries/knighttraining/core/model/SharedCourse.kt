package com.knightindustries.knighttraining.core.model

import java.net.URI
import java.time.Instant
import java.util.UUID

/**
 * A Chessable/ChessMood course added by sharing its page, remembered by platform identity so the
 * resource can be linked to its `catalog_course` row (iOS `SharedCourse`). Kept in its own table,
 * never in `catalog_course`, which the catalog sync owns and may rebuild: this row survives any
 * catalog change, and the merge re-links by id whenever a matching course exists.
 */
data class SharedCourse(
    val id: UUID = UUID.randomUUID(),
    val resourceId: UUID,
    val identity: Identity,
    val url: String,
    /** The title at share time, for the title fallback; not the resource's, which may be edited. */
    val title: String,
    val createdAt: Instant = Instant.now(),
) {
    /** Which course a link points at: the catalog's own id when it can be found, ChessMood's slug otherwise. */
    data class Identity(val platform: String, val externalId: String?, val slug: String?) {
        companion object {
            /**
             * `null` for anything that isn't a course page.
             * - Chessable: `chessable.com/<slug>/course/<id>/`, the catalog's id in the URL.
             * - ChessMood: `chessmood.com/course/<slug>`; the id is in the page's cover image path
             *   (`cdn.chessmood.com/…/images/courses/<id>/…`). Without it, the slug alone.
             */
            fun from(url: String, imageUrl: String?): Identity? {
                val uri = runCatching { URI(url) }.getOrNull() ?: return null
                val host = uri.host?.lowercase() ?: return null
                val path = uri.path.orEmpty().split("/").filter { it.isNotEmpty() }
                fun isHost(domain: String) = host == domain || host.endsWith(".$domain")
                fun after(markers: Set<String>): String? {
                    val index = path.indexOfFirst { it.lowercase() in markers }
                    return if (index >= 0 && index + 1 < path.size) path[index + 1] else null
                }
                return when {
                    isHost("chessable.com") -> after(setOf("course"))?.takeIf { id -> id.isNotEmpty() && id.all(Char::isDigit) }
                        ?.let { Identity(CatalogCourse.CHESSABLE, it, null) }
                    isHost("chessmood.com") -> after(setOf("course", "courses"))
                        ?.let { slug -> Identity(CatalogCourse.CHESSMOOD, imageUrl?.let(::chessMoodCourseId), slug.lowercase()) }
                    else -> null
                }
            }

            /** The numeric id in a ChessMood course image URL (`…/images/courses/109/….webp`). */
            private fun chessMoodCourseId(imageUrl: String): String? {
                val uri = runCatching { URI(imageUrl) }.getOrNull() ?: return null
                if (uri.host?.lowercase()?.endsWith("chessmood.com") != true) return null
                val path = uri.path.orEmpty().split("/")
                val index = path.indexOf("courses")
                val id = if (index >= 0 && index + 1 < path.size) path[index + 1] else return null
                return id.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            }
        }
    }
}
