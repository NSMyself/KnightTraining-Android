package com.knightindustries.knighttraining.core.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class LinkMetadata(
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val author: String? = null,
    val isbn: String? = null,
    val publisher: String? = null,
    val publishedDate: Instant? = null,
)

/**
 * Best-effort scrape of a page's Open Graph tags and Schema.org JSON-LD, falling back to
 * `<title>`/`<meta name="author">` (iOS `LinkMetadataService`). Regex over raw HTML, no parser:
 * enough for server-rendered pages. Every failure returns `null`, so adding a resource by hand is
 * never blocked.
 */
class LinkMetadataService(private val http: HttpGet = OkHttpGet()) {
    suspend fun fetch(url: String): LinkMetadata? {
        val response = http.get(url, mapOf("User-Agent" to "Mozilla/5.0 (compatible; KnightTrainingBot/1.0)")) ?: return null
        if (!response.isSuccess) return null
        return parse(response.body)
    }

    companion object {
        /**
         * Markers only a bot-check interstitial carries: Anubis, Cloudflare's challenge page, and
         * DataDome. Its "Human verification" title was once saved as a book's name (iOS).
         */
        private val botCheckMarkers = listOf("anubis_challenge", "_cf_chl_opt", "captcha-delivery.com")

        fun isBotCheckPage(html: String): Boolean = botCheckMarkers.any { it in html }

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(html: String): LinkMetadata? {
            if (isBotCheckPage(html)) return null
            val jsonLd = bookJsonLd(html)
            val title = (metaProperty("og:title", html) ?: titleTag(html))?.htmlDecoded()
            val description = metaProperty("og:description", html)?.htmlDecoded()
            // Attribute values are entity-encoded: "…&amp;theme=brown" must decode, or every
            // query parameter after the first is corrupted (Lichess study images, iOS).
            val image = metaProperty("og:image", html)?.htmlDecoded()
            val author = jsonLd?.get("author")?.names()
                ?: metaProperty("book:author", html)?.htmlDecoded()
                ?: metaName("author", html)?.htmlDecoded()
            val isbn = (jsonLd?.get("isbn") as? JsonPrimitive)?.contentOrNull ?: metaProperty("book:isbn", html)
            val publisher = jsonLd?.get("publisher")?.names()
            val published = (jsonLd?.get("datePublished") as? JsonPrimitive)?.contentOrNull?.let(::parseFlexibleDate)
            if (title == null && description == null && image == null && author == null) return null
            return LinkMetadata(title, description, image, author, isbn, publisher, published)
        }

        private fun metaProperty(property: String, html: String) = meta("property", property, html)

        private fun metaName(name: String, html: String) = meta("name", name, html)

        private fun meta(attribute: String, value: String, html: String): String? {
            val escaped = Regex.escape(value)
            return Regex("""<meta[^>]+$attribute=["']$escaped["'][^>]+content=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                ?: Regex("""<meta[^>]+content=["']([^"']*)["'][^>]+$attribute=["']$escaped["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
        }

        private fun titleTag(html: String) =
            Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.trim()?.ifEmpty { null }

        private val jsonLdPattern = Regex("""<script[^>]+type=["']application/ld\+json["'][^>]*>(.*?)</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        /** The first Book/Product block: top-level, in an array, or under `@graph`. */
        private fun bookJsonLd(html: String): JsonObject? {
            for (match in jsonLdPattern.findAll(html)) {
                val element = runCatching { json.parseToJsonElement(match.groupValues[1]) }.getOrNull() ?: continue
                val candidates = when (element) {
                    is JsonObject -> listOf(element) + ((element["@graph"] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty())
                    is JsonArray -> element.filterIsInstance<JsonObject>()
                    else -> emptyList()
                }
                candidates.firstOrNull(::isBookLike)?.let { return it }
            }
            return null
        }

        private fun isBookLike(item: JsonObject): Boolean {
            fun matches(type: String) = type.contains("book", ignoreCase = true) || type.contains("product", ignoreCase = true)
            return when (val type = item["@type"]) {
                is JsonPrimitive -> type.contentOrNull?.let(::matches) == true
                is JsonArray -> type.any { (it as? JsonPrimitive)?.contentOrNull?.let(::matches) == true }
                else -> false
            }
        }

        /** A name, a `{name}` object, or a list of either (several authors). */
        private fun JsonElement.names(): String? = when (this) {
            is JsonPrimitive -> contentOrNull
            is JsonObject -> (this["name"] as? JsonPrimitive)?.contentOrNull
            is JsonArray -> mapNotNull { it.names() }.takeIf { it.isNotEmpty() }?.joinToString(", ")
        }

        private fun String.htmlDecoded() = replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

        /** ISO dates from JSON-LD and Google; whatever Open Library holds ("March 2012", "Nov 1, 2012"). */
        fun parseFlexibleDate(text: String): Instant? {
            val trimmed = text.trim()
            fun day(date: LocalDate) = date.atStartOfDay(ZoneOffset.UTC).toInstant()
            runCatching { return day(LocalDate.parse(trimmed)) }
            runCatching { return day(YearMonth.parse(trimmed).atDay(1)) }
            trimmed.toIntOrNull()?.takeIf { it in 1000..9999 }?.let { return day(LocalDate.of(it, 1, 1)) }
            for (pattern in listOf("MMMM yyyy", "MMM yyyy")) {
                runCatching { return day(YearMonth.parse(trimmed, DateTimeFormatter.ofPattern(pattern, Locale.US)).atDay(1)) }
            }
            for (pattern in listOf("MMMM d, yyyy", "MMM d, yyyy")) {
                runCatching { return day(LocalDate.parse(trimmed, DateTimeFormatter.ofPattern(pattern, Locale.US))) }
            }
            return null
        }
    }
}
