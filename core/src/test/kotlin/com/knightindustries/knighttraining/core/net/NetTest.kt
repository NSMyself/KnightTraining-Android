package com.knightindustries.knighttraining.core.net

import com.knightindustries.knighttraining.core.net.ResourceLinks.guessKindCode
import com.knightindustries.knighttraining.core.net.ResourceLinks.validIsbn
import com.knightindustries.knighttraining.core.store.StoreTest
import com.knightindustries.knighttraining.core.store.catalogCourses
import com.knightindustries.knighttraining.core.store.hasCatalogCourses
import com.knightindustries.knighttraining.core.store.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Ports of iOS `SharedResourceKindGuessTests` and `LinkMetadataServiceTests`, plus the book
 * lookup and catalog sync, all against canned responses: no network.
 */
class NetTest : StoreTest() {
    private class FakeHttp(private val responses: Map<String, HttpResponse?>) : HttpGet {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()

        override suspend fun get(url: String, headers: Map<String, String>): HttpResponse? {
            requests += url to headers
            return responses.entries.firstOrNull { url.startsWith(it.key) }?.value
        }
    }

    private fun ok(body: String, headers: Map<String, String> = emptyMap()) = HttpResponse(200, body, headers)

    // MARK: - Kind guessing

    @Test
    fun youTubeLinksAreVideos() {
        listOf(
            "https://www.youtube.com/watch?v=HoPbWOhUq3I", "https://youtu.be/HoPbWOhUq3I", "https://m.youtube.com/watch?v=HoPbWOhUq3I",
            "https://www.youtube.com/shorts/abc123", "https://www.youtube.com/playlist?list=PLQsLDm9Rq9bENTaXFGh3aTGjlCUXWn5tl",
        ).forEach { assertEquals(it, "video", guessKindCode(it)) }
    }

    @Test
    fun lichessStudiesAreStudiesAndOtherLichessPagesArticles() {
        assertEquals("lichess_study", guessKindCode("https://lichess.org/study/abcd1234"))
        assertEquals("lichess_study", guessKindCode("https://lichess.org/study/abcd1234/efgh5678"))
        assertEquals("article", guessKindCode("https://lichess.org/@/someone"))
        assertEquals("article", guessKindCode("https://lichess.org/@/someone/blog/some-post/xyz"))
    }

    @Test
    fun courseSitesAreCoursesOnlyUnderACoursePath() {
        assertEquals("course", guessKindCode("https://www.chessable.com/keep-it-sharp-kings-indian/course/416495/"))
        assertEquals("course", guessKindCode("https://chessmood.com/course/spartan-shield"))
        assertEquals("article", guessKindCode("https://www.chessable.com/blog/some-post/"))
        assertEquals("article", guessKindCode("https://chessmood.com/blog/some-post"))
    }

    @Test
    fun lookalikeHostsAreNotMatched() {
        assertEquals("article", guessKindCode("https://notyoutube.com/watch?v=1"))
        assertEquals("article", guessKindCode("https://youtube.com.example.org/watch?v=1"))
        assertEquals("article", guessKindCode("https://notnewinchess.com/some-book"))
        assertEquals("article", guessKindCode("https://newinchess.com.example.org/some-book"))
        assertEquals("article", guessKindCode("https://en.chessbase.com/post/some-news"))
    }

    @Test
    fun aValidIsbnMakesABookButHostRulesOutrankIt() {
        val page = "https://www.example-bookshop.com/mastering-practical-endgames"
        assertEquals("book", guessKindCode(page, "978-90-5691-976-4"))
        assertEquals("book", guessKindCode(page, "90-5691-976-8"))
        assertEquals("article", guessKindCode(page))
        assertEquals("article", guessKindCode(page, "978-90-5691-976-5"))
        assertEquals("article", guessKindCode(page, "12345"))
        assertEquals("video", guessKindCode("https://www.youtube.com/watch?v=HoPbWOhUq3I", "9789056919764"))
    }

    @Test
    fun isbnValidation() {
        assertEquals("9789056919764", validIsbn("978-90-5691-976-4"))
        assertEquals("080442957X", validIsbn("0-8044-2957-X"))
        assertNull(validIsbn("0-8044-2957-5"))
        assertNull(validIsbn("97890569197X4"))
    }

    @Test
    fun publisherPagesAreBooksExceptHomeAndBlog() {
        listOf(
            "https://www.newinchess.com/mastering-practical-endgames", "https://www.newinchess.com/logical-chess-move-by-move-7815",
            "https://www.qualitychess.co.uk/products/some-book/123/", "https://forwardchess.com/product/chess-evolution1-the-fundamentals",
        ).forEach { assertEquals(it, "book", guessKindCode(it)) }
        listOf(
            "https://www.newinchess.com/", "https://www.newinchess.com", "https://www.qualitychess.co.uk/blog/some-post",
            "https://thinkerspublishing.com/news/new-releases",
        ).forEach { assertEquals(it, "article", guessKindCode(it)) }
    }

    // MARK: - Link metadata

    /** Entity-encoded attribute values must decode, or every query parameter after the first breaks. */
    @Test
    fun anOgImageUrlIsEntityDecoded() {
        val html = """
            <html><head><meta property="og:title" content="thibault&#39;s Study" />
            <meta property="og:image" content="https://lichess1.org/export/fen.gif?fen=rnbqkbnr%2F8&amp;color=white&amp;theme=brown" />
            </head></html>
        """.trimIndent()
        val metadata = LinkMetadataService.parse(html)!!
        assertEquals("thibault's Study", metadata.title)
        assertEquals("https://lichess1.org/export/fen.gif?fen=rnbqkbnr%2F8&color=white&theme=brown", metadata.imageUrl)
    }

    @Test
    fun jsonLdBookFieldsAreReadEvenUnderGraph() {
        val html = """
            <title>Fallback</title>
            <script type="application/ld+json">{"@graph":[{"@type":"WebPage"},{"@type":"Book","author":[{"name":"Jacob Aagaard"},{"name":"John Shaw"}],
            "isbn":"9781907982774","publisher":{"name":"Quality Chess"},"datePublished":"2014-03"}]}</script>
        """.trimIndent()
        val metadata = LinkMetadataService.parse(html)!!
        assertEquals("Fallback", metadata.title)
        assertEquals("Jacob Aagaard, John Shaw", metadata.author)
        assertEquals("9781907982774", metadata.isbn)
        assertEquals("Quality Chess", metadata.publisher)
        assertEquals(LocalDate.of(2014, 3, 1).atStartOfDay(ZoneOffset.UTC).toInstant(), metadata.publishedDate)
    }

    @Test
    fun contentBeforePropertyStillMatchesAndMetaAuthorIsTheLastResort() {
        val metadata = LinkMetadataService.parse("""<meta content="Endgame Manual" property="og:title"><meta name="author" content="Dvoretsky">""")!!
        assertEquals("Endgame Manual", metadata.title)
        assertEquals("Dvoretsky", metadata.author)
    }

    @Test
    fun aBotCheckPageYieldsNothing() {
        assertNull(LinkMetadataService.parse("""<title>Human verification</title><script id="anubis_challenge"></script>"""))
        assertTrue(LinkMetadataService.isBotCheckPage("""<script>window._cf_chl_opt={cvId:'3'}</script>"""))
        assertFalse(LinkMetadataService.isBotCheckPage("""<script src="/cdn-cgi/challenge-platform/scripts/jsd/main.js"></script>"""))
    }

    @Test
    fun aPageWithNothingUsefulYieldsNothing() {
        assertNull(LinkMetadataService.parse("<html><body>Hello</body></html>"))
    }

    @Test
    fun flexibleDates() {
        fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant()
        assertEquals(day(2012, 11, 1), LinkMetadataService.parseFlexibleDate("2012-11-01"))
        assertEquals(day(2012, 1, 1), LinkMetadataService.parseFlexibleDate("2012"))
        assertEquals(day(2012, 3, 1), LinkMetadataService.parseFlexibleDate("March 2012"))
        assertEquals(day(2012, 11, 1), LinkMetadataService.parseFlexibleDate("Nov 1, 2012"))
        assertNull(LinkMetadataService.parseFlexibleDate("soon"))
    }

    // MARK: - Book lookup

    private val google = """{"items":[{"volumeInfo":{"title":"Amateur to IM","authors":["Jonathan Hawkins"],
        "publisher":"Mongoose Press","publishedDate":"2012-11-01","description":"Proven ideas.",
        "imageLinks":{"thumbnail":"http://books.google.com/x.jpg"}}}]}"""

    private val openLibrary = """{"ISBN:9781936277445":{"title":"Amateur To Im Proven Ideas","authors":[{"name":"Jonathan Hawkins"}],
        "publishers":[{"name":"Mongoose"}],"publish_date":"2012","cover":{"medium":"https://covers.openlibrary.org/m.jpg"}}}"""

    @Test
    fun aCompleteGoogleAnswerIsUsedAsIsWithAnHttpsCover() = runTest {
        val http = FakeHttp(mapOf("https://www.googleapis.com" to ok(google)))
        val book = BookLookupService(http).lookup("978-1-936277-44-5")!!
        assertEquals("Amateur to IM", book.title)
        assertEquals("https://books.google.com/x.jpg", book.imageUrl)
        assertEquals(1, http.requests.size)
    }

    @Test
    fun openLibraryFillsWhatGoogleLeavesOutOrEverythingWhenItFails() = runTest {
        val partialGoogle = """{"items":[{"volumeInfo":{"title":"Amateur to IM"}}]}"""
        val merged = BookLookupService(FakeHttp(mapOf("https://www.googleapis.com" to ok(partialGoogle), "https://openlibrary.org" to ok(openLibrary)))).lookup("9781936277445")!!
        assertEquals("Amateur to IM", merged.title)
        assertEquals(listOf("Jonathan Hawkins"), merged.authors)
        assertEquals("https://covers.openlibrary.org/m.jpg", merged.imageUrl)

        val quotaHit = BookLookupService(FakeHttp(mapOf("https://www.googleapis.com" to HttpResponse(429, "", emptyMap()), "https://openlibrary.org" to ok(openLibrary)))).lookup("9781936277445")!!
        assertEquals("Amateur To Im Proven Ideas", quotaHit.title)
        assertEquals("Mongoose", quotaHit.publisher)
    }

    @Test
    fun theEnricherPrefersTheBookRecordOverTheShopPage() = runTest {
        val shopPage = """<meta property="og:title" content="Amateur to IM: Amazon.com: Books"><meta property="og:image" content="https://amazon.com/logo.png">
            <script type="application/ld+json">{"@type":"Product","isbn":"9781936277445"}</script>"""
        val http = FakeHttp(mapOf("https://www.amazon.com" to ok(shopPage), "https://www.googleapis.com" to ok(google)))
        val enriched = ResourceEnricher(LinkMetadataService(http), BookLookupService(http)).enrich("https://www.amazon.com/dp/1936277441")
        assertEquals("book", enriched.kindCode)
        assertEquals("Amateur to IM", enriched.title)
        assertEquals("https://books.google.com/x.jpg", enriched.imageUrl)
        assertEquals("Jonathan Hawkins", enriched.author)
    }

    @Test
    fun anUnreachablePageStillGetsAKind() = runTest {
        val enriched = ResourceEnricher(LinkMetadataService(FakeHttp(emptyMap())), BookLookupService(FakeHttp(emptyMap()))).enrich("https://lichess.org/study/abc")
        assertEquals("lichess_study", enriched.kindCode)
        assertNull(enriched.title)
    }

    // MARK: - Catalog sync

    private class MemoryEtags(var value: String? = null) : CatalogSync.EtagStore {
        override suspend fun get() = value

        override suspend fun set(etag: String) {
            value = etag
        }
    }

    private val catalogJson = """{"chessable":[{"id":"416495","title":"Keep It Sharp","author":"Someone","languages":["es","en"]}],
        "chessmood":[{"id":"12","title":"Endgame Roadmap","image_url":"https://cdn/x.png","topic":"tactics","min_rating":1200,"duration_minutes":600}]}"""

    @Test
    fun aNewCatalogIsStoredWithItsEtag() = test {
        val etags = MemoryEtags()
        val synced = CatalogSync(store, etags, FakeHttp(mapOf(CatalogSync.CATALOG_URL to ok(catalogJson, mapOf("etag" to "\"v1\""))))).syncIfNeeded()

        assertTrue(synced)
        assertEquals("\"v1\"", etags.value)
        val courses = store.catalogCourses()
        assertEquals(2, courses.size)
        assertEquals("puzzles", courses.first { it.isChessmood }.topicCode)
        assertEquals(listOf("es", "en"), courses.first { !it.isChessmood }.languages)
    }

    @Test
    fun theStoredEtagIsSentAndA304ChangesNothing() = test {
        val etags = MemoryEtags()
        CatalogSync(store, etags, FakeHttp(mapOf(CatalogSync.CATALOG_URL to ok(catalogJson, mapOf("etag" to "\"v1\""))))).syncIfNeeded()
        val http = FakeHttp(mapOf(CatalogSync.CATALOG_URL to HttpResponse(304, "", emptyMap())))
        assertFalse(CatalogSync(store, etags, http).syncIfNeeded())
        assertEquals("\"v1\"", http.requests.single().second["If-None-Match"])
        assertEquals(2, store.catalogCourses().size)
    }

    /** iOS 2026-11-24: an ETag kept after the table was wiped got a 304 forever. */
    @Test
    fun anEmptyCatalogIsFetchedInFullDespiteAStoredEtag() = test {
        val etags = MemoryEtags("\"v1\"")
        val http = FakeHttp(mapOf(CatalogSync.CATALOG_URL to ok(catalogJson, mapOf("etag" to "\"v2\""))))
        assertFalse(store.hasCatalogCourses())

        assertTrue(CatalogSync(store, etags, http).syncIfNeeded())

        assertNull(http.requests.single().second["If-None-Match"])
        assertTrue(store.hasCatalogCourses())
        assertEquals("\"v2\"", etags.value)
    }

    /**
     * An undecodable payload keeps the catalog already stored. (A quoted number like `"1200"`
     * decodes here, where iOS's decoder rejects it; that leniency is harmless.)
     */
    @Test
    fun anUndecodablePayloadKeepsTheOldCatalog() = test {
        val etags = MemoryEtags()
        CatalogSync(store, etags, FakeHttp(mapOf(CatalogSync.CATALOG_URL to ok(catalogJson, mapOf("etag" to "a"))))).syncIfNeeded()
        val broken = """{"chessable":[],"chessmood":[{"id":"12","title":"X","min_rating":"about 1200"}]}"""
        assertFalse(CatalogSync(store, etags, FakeHttp(mapOf(CatalogSync.CATALOG_URL to ok(broken, mapOf("etag" to "b"))))).syncIfNeeded())
        assertEquals("a", etags.value)
        assertEquals(2, store.catalogCourses().size)
    }

    @Test
    fun parseStampsFetchTime() {
        val at = Instant.ofEpochSecond(42)
        assertTrue(CatalogSync.parse(catalogJson, at)!!.all { it.fetchedAt == at })
    }
}
