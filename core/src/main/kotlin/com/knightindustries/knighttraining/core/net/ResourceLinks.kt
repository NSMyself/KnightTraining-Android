package com.knightindustries.knighttraining.core.net

import com.knightindustries.knighttraining.core.model.ResourceKind
import java.net.URI

/** What a link alone says about the resource behind it (iOS `SharedResourceImportService`). */
object ResourceLinks {
    /**
     * Chess book publishers, plus Forward Chess (their books as ebooks). ChessBase is left out on
     * purpose: software and video. Checked reachable by iOS on 2026-10-11.
     */
    val bookPublisherHosts = listOf(
        "newinchess.com", "qualitychess.co.uk", "gambitbooks.com", "batsfordbooks.com",
        "thinkerspublishing.com", "chessstars.com", "russell-enterprises.com", "mongoosepress.com",
        "elkandruby.com", "mcfarlandbooks.com", "olms.de", "hardingesimpole.co.uk", "forwardchess.com",
    )

    /** First path components on a publisher's site that are articles, not books. */
    private val nonBookSections = setOf("blog", "blogs", "news", "article", "articles")

    /**
     * A conservative guess at a link's kind: a wrong guess costs a trip to Edit, so only
     * unambiguous hosts get one.
     * - YouTube → video. A Lichess study → lichess_study.
     * - Chessable/ChessMood under a `course(s)` path → course (both also run blogs).
     * - A book publisher's page → book, except its homepage and blog/news pages.
     * - Anywhere else, a valid ISBN → book; otherwise article.
     */
    fun guessKindCode(url: String, isbn: String? = null): String {
        val fallback = if (isbn?.let(::validIsbn) != null) ResourceKind.BOOK else ResourceKind.ARTICLE
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return fallback
        val host = uri.host?.lowercase() ?: return fallback
        val path = uri.path.orEmpty().split("/").filter { it.isNotEmpty() }.map { it.lowercase() }
        fun isHost(domain: String) = host == domain || host.endsWith(".$domain")

        return when {
            isHost("youtube.com") || isHost("youtu.be") -> ResourceKind.VIDEO
            isHost("lichess.org") && path.firstOrNull() == "study" -> ResourceKind.LICHESS_STUDY
            (isHost("chessable.com") || isHost("chessmood.com")) && ("course" in path || "courses" in path) -> ResourceKind.COURSE
            bookPublisherHosts.any(::isHost) -> {
                val section = path.firstOrNull()
                if (section == null || section in nonBookSections) ResourceKind.ARTICLE else ResourceKind.BOOK
            }
            else -> fallback
        }
    }

    /**
     * The ISBN if its check digit is right (ISBN-13, or ISBN-10 with a possible final X), so a
     * stray number never passes as one. Hyphens and spaces are ignored.
     */
    fun validIsbn(raw: String): String? {
        val characters = raw.uppercase().filter { it.isDigit() || it == 'X' }
        val digits = characters.filter(Char::isDigit).map { it - '0' }
        return when {
            characters.length == 13 && digits.size == 13 -> {
                val sum = digits.withIndex().sumOf { (i, d) -> d * if (i % 2 == 0) 1 else 3 }
                characters.takeIf { sum % 10 == 0 }
            }
            characters.length == 10 && (digits.size == 10 || (digits.size == 9 && characters.last() == 'X')) -> {
                val values = if (digits.size == 9) digits + 10 else digits
                val sum = values.withIndex().sumOf { (i, d) -> d * (10 - i) }
                characters.takeIf { sum % 11 == 0 }
            }
            else -> null
        }
    }

    /** A title for a link nothing could describe: its host, without `www.`. */
    fun fallbackTitle(url: String): String =
        runCatching { URI(url.trim()).host?.removePrefix("www.") }.getOrNull() ?: url
}
