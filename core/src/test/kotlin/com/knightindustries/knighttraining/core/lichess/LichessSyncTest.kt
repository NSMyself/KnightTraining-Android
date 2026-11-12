package com.knightindustries.knighttraining.core.lichess

import com.knightindustries.knighttraining.core.model.LichessActivityBatch
import com.knightindustries.knighttraining.core.model.Milestone
import com.knightindustries.knighttraining.core.model.RatingType
import com.knightindustries.knighttraining.core.net.HttpGet
import com.knightindustries.knighttraining.core.net.HttpResponse
import com.knightindustries.knighttraining.core.store.StoreTest
import com.knightindustries.knighttraining.core.store.allResources
import com.knightindustries.knighttraining.core.store.eloSnapshots
import com.knightindustries.knighttraining.core.store.ensureLichessAccount
import com.knightindustries.knighttraining.core.store.lichessAccount
import com.knightindustries.knighttraining.core.store.milestonesSnapshot
import com.knightindustries.knighttraining.core.store.pendingLichessActivityBatches
import com.knightindustries.knighttraining.core.store.saveMilestone
import com.knightindustries.knighttraining.core.store.saveResource
import com.knightindustries.knighttraining.core.store.test
import com.knightindustries.knighttraining.core.store.updateLastGameSyncAt
import com.knightindustries.knighttraining.core.store.updateLastPuzzleSyncAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

/** The sync jobs against canned payloads in Lichess's real shapes: no network. */
class LichessSyncTest : StoreTest() {
    private class FakeHttp : HttpGet {
        val routes = mutableMapOf<String, HttpResponse?>()
        val requests = mutableListOf<Pair<String, Map<String, String>>>()

        override suspend fun get(url: String, headers: Map<String, String>): HttpResponse? {
            requests += url to headers
            return routes.entries.firstOrNull { url.startsWith(it.key) }?.value
        }
    }

    private val http = FakeHttp()
    private var unauthorized = 0
    private val client get() = LichessClient(token = { "token" }, onUnauthorized = { unauthorized++ }, http = http, gate = LichessRequestGate { now })
    private val sync get() = LichessSync(store, client, now = { now })

    private fun ok(body: String) = HttpResponse(200, body, emptyMap())

    private fun ms(instant: Instant) = instant.toEpochMilli()

    private val accountJson = """
        {"id":"tester","username":"Tester","title":"FM","profile":{"flag":"PT","bio":"hi"},"createdAt":1500000000000,
         "perfs":{"blitz":{"games":10,"rating":1700,"rd":50,"prog":3},"rapid":{"games":5,"rating":1810,"rd":60,"prog":-2,"prov":true},
         "puzzle":{"games":100,"rating":2000,"rd":70,"prog":0},"storm":{"runs":3,"score":20},"streak":{"runs":189,"score":34}}}
    """.trimIndent()

    // MARK: - Ratings

    @Test
    fun ratingsBackfillOnceThenCaptureOncePerDay() = test {
        http.routes["https://lichess.org/api/account"] = ok(accountJson)
        http.routes["https://lichess.org/api/user/Tester/rating-history"] = ok(
            """[{"name":"Bullet","points":[]},{"name":"Rapid","points":[[2026,9,1,1750],[2026,10,1,1800]]},
               {"name":"Puzzles","points":[[2026,10,2,1990]]},{"name":"Racing Kings","points":[[2026,1,1,1500]]}]""",
        )
        val milestone = Milestone(kind = Milestone.ELO, ratingType = RatingType.RAPID, targetRating = 1805)
        store.saveMilestone(milestone)

        assertTrue("The backfill's latest rapid is 1800, short of 1805", sync.syncRatings().isEmpty())
        val account = store.lichessAccount()!!
        assertEquals("FM", account.title)
        assertEquals("PT", account.flag)
        assertEquals(listOf(1750, 1800), store.eloSnapshots(account.id, RatingType.RAPID).map { it.rating })
        assertEquals("Lichess months are 0-based", calendar.startOf(java.time.LocalDate.of(2026, 10, 1)), store.eloSnapshots(account.id, RatingType.RAPID).first().capturedAt)
        assertTrue(store.eloSnapshots(account.id, RatingType.BULLET).isEmpty())

        // Second sync today: the backfill has no point for today, so today's ratings are captured.
        assertEquals(listOf(milestone.id), sync.syncRatings().map { it.id })
        assertEquals(listOf(1750, 1800, 1810), store.eloSnapshots(account.id, RatingType.RAPID).map { it.rating })

        sync.syncRatings()
        assertEquals("Once per day", 3, store.eloSnapshots(account.id, RatingType.RAPID).size)
        assertEquals(1810, store.milestonesSnapshot().latestRatings[RatingType.RAPID])
    }

    @Test
    fun aFailedRatingSyncIsSilent() = test {
        assertTrue(sync.syncRatings().isEmpty())
        assertNull(store.lichessAccount())
    }

    // MARK: - Games

    private fun game(id: String, start: Instant, minutes: Long, speed: String = "rapid", status: String = "resign", clock: String = """{"initial":600,"increment":0,"totalTime":600}""") =
        """{"id":"$id","rated":true,"variant":"standard","speed":"$speed","perf":"$speed","createdAt":${ms(start)},"lastMoveAt":${ms(start.plusSeconds(minutes * 60))},"status":"$status","players":{},"clock":$clock}"""

    @Test
    fun theFirstGameSyncOnlyRecordsABaseline() = test {
        store.ensureLichessAccount("Tester", null, null)
        sync.syncGames()
        assertTrue("Never backfills: no request at all", http.requests.isEmpty())
        assertEquals(now, store.lichessAccount()!!.lastGameSyncAt)
        assertTrue(store.pendingLichessActivityBatches().isEmpty())
    }

    @Test
    fun gamesAreBatchedBySitting() = test {
        val id = store.ensureLichessAccount("Tester", null, null)
        val since = now.minusSeconds(6 * 3600)
        store.updateLastGameSyncAt(id, since)
        val t = since.plusSeconds(600)
        val games = listOf(
            game("r1", t, 12),
            game("r2", t.plusSeconds(15 * 60), 14),
            game("ab", t.plusSeconds(30 * 60), 0, status = "aborted"),
            game("r3", t.plusSeconds(31 * 60), 20, clock = """{"initial":900,"increment":10}"""),
            game("b1", t.plusSeconds(55 * 60), 6, speed = "blitz", clock = """{"initial":180,"increment":2}"""),
            game("r4", t.plusSeconds(4 * 3600), 11),
        )
        http.routes["https://lichess.org/api/games/user/Tester"] = ok(games.joinToString("\n") + "\nnot json\n")
        sync.syncGames()

        val batches = store.pendingLichessActivityBatches()
        assertEquals(listOf(listOf("r1", "r2", "r3"), listOf("b1"), listOf("r4")), batches.map { it.externalIds })
        val rapid = batches.first()
        assertEquals("rapid", rapid.category)
        assertEquals("Measured: each game's own span", (12 + 14 + 20) * 60, rapid.totalDurationSeconds)
        assertEquals("The most common time control", "10 min", rapid.timeControlLabel)
        assertEquals("3+2", batches[1].timeControlLabel)
        assertEquals(t.plusSeconds(4 * 3600), store.lichessAccount()!!.lastGameSyncAt)

        val query = http.requests.single().first
        assertTrue(query.contains("perfType=rapid%2Cclassical%2Cblitz") && query.contains("since=${ms(since)}"))

        // The same window again offers nothing twice.
        sync.syncGames()
        assertEquals(3, store.pendingLichessActivityBatches().size)
    }

    // MARK: - Puzzles

    private fun puzzle(id: String, at: Instant) = """{"date":${ms(at)},"win":true,"puzzle":{"id":"$id","fen":"x","plays":100,"rating":1500,"solution":[],"themes":[]}}"""

    @Test
    fun puzzlesBaselineThenBatchByGap() = test {
        val id = store.ensureLichessAccount("Tester", null, null)
        sync.syncPuzzles()
        assertEquals(now, store.lichessAccount()!!.lastPuzzleSyncAt)
        assertTrue(http.requests.isEmpty())

        val since = now.minusSeconds(5 * 3600)
        store.updateLastPuzzleSyncAt(id, since)
        val t = since.plusSeconds(60)
        http.routes["https://lichess.org/api/puzzle/activity"] = ok(
            listOf(puzzle("p1", t), puzzle("p2", t.plusSeconds(120)), puzzle("p3", t.plusSeconds(400)), puzzle("p4", t.plusSeconds(3 * 3600))).joinToString("\n"),
        )
        sync.syncPuzzles()
        val batches = store.pendingLichessActivityBatches()
        assertEquals(listOf(listOf("p1", "p2", "p3"), listOf("p4")), batches.map { it.externalIds })
        assertEquals("The bounding box", 400, batches.first().totalDurationSeconds)
        assertEquals("Bearer token", http.requests.single().second["Authorization"])
    }

    // MARK: - Storm

    private fun storm(dayId: String, runs: Int, time: Int) =
        ok("""{"high":{"allTime":30},"days":[{"_id":"$dayId","combo":10,"errors":2,"highest":1800,"moves":40,"runs":$runs,"score":${runs * 9},"time":$time}]}""")

    @Test
    fun stormCountsTheGrowthOfTheLatestDay() = test {
        store.ensureLichessAccount("Tester", null, null)
        http.routes["https://lichess.org/api/storm/dashboard/Tester"] = storm("2026/11/11", 2, 360)
        sync.syncStorm()
        assertTrue("A baseline only", store.pendingLichessActivityBatches().isEmpty())

        http.routes["https://lichess.org/api/storm/dashboard/Tester"] = storm("2026/11/11", 5, 900)
        sync.syncStorm()
        val batch = store.pendingLichessActivityBatches().single()
        assertEquals(LichessActivityBatch.STORM, batch.kind)
        assertEquals(3, batch.itemCount)
        assertEquals(540, batch.totalDurationSeconds)
        assertEquals(now.minusSeconds(540), batch.firstItemAt)

        // A new day: everything on it is new.
        http.routes["https://lichess.org/api/storm/dashboard/Tester"] = storm("2026/11/12", 1, 180)
        sync.syncStorm()
        assertEquals(listOf(3, 1), store.pendingLichessActivityBatches().map { it.itemCount })
    }

    // MARK: - Streak

    @Test
    fun streakRunsComeFromTheActivityFeed() = test {
        store.ensureLichessAccount("Tester", null, null)
        val dayStart = Instant.parse("2026-11-11T00:00:00Z")
        fun feed(runs: Int, score: Int) = ok(
            """[{"interval":{"start":${ms(dayStart)},"end":${ms(dayStart.plusSeconds(86_400))}},"streak":{"runs":$runs,"score":$score},"games":{}},
               {"interval":{"start":${ms(dayStart.minusSeconds(86_400))},"end":${ms(dayStart)}},"puzzles":{"score":{"win":3}}}]""",
        )
        http.routes["https://lichess.org/api/user/Tester/activity"] = feed(2, 10)
        sync.syncStreak()
        assertTrue(store.pendingLichessActivityBatches().isEmpty())
        assertEquals(2, store.lichessAccount()!!.lastStreakRuns)

        val firstSync = now
        now = now.plusSeconds(3600)
        http.routes["https://lichess.org/api/user/Tester/activity"] = feed(5, 16)
        sync.syncStreak()
        val batch = store.pendingLichessActivityBatches().single()
        assertEquals(3, batch.itemCount)
        assertEquals(16, batch.bestScore)
        assertEquals(0, batch.totalDurationSeconds)
        assertEquals(firstSync, batch.firstItemAt)
        assertEquals(now, batch.lastItemAt)
    }

    // MARK: - Client rules

    @Test
    fun a429PausesEveryRequestForAMinute() = test {
        http.routes["https://lichess.org/api/user/Tester/activity"] = HttpResponse(429, "", emptyMap())
        val gate = LichessRequestGate { now }
        val client = LichessClient(token = { null }, http = http, gate = gate)
        runCatching { client.fetchActivity("Tester") }
        try {
            client.fetchStormDashboard("Tester")
            fail("Expected the cooldown")
        } catch (expected: LichessException.RateLimited) {
            assertEquals(now.plusSeconds(60), expected.until)
        }
        assertEquals("The second call never reached the network", 1, http.requests.size)

        now = now.plusSeconds(61)
        http.routes["https://lichess.org/api/storm/dashboard/Tester"] = storm("2026/11/11", 1, 180)
        assertEquals(1, client.fetchStormDashboard("Tester").days.single().runs)
    }

    @Test
    fun a401ToASignedRequestReportsTheLoginGone() = test {
        http.routes["https://lichess.org/api/account"] = HttpResponse(401, "{}", emptyMap())
        runCatching { client.fetchAccount() }
        assertEquals(1, unauthorized)
    }

    // MARK: - Studies

    @Test
    fun newStudiesJoinTheLibraryOnce() = test {
        store.ensureLichessAccount("Tester", null, null)
        val kind = store.resourceKind("lichess_study")!!
        // Added by hand earlier, with a different title: matched by its id, never duplicated.
        store.saveResource(com.knightindustries.knighttraining.core.model.Resource(kind = kind, title = "My Caro-Kann", url = "https://lichess.org/study/AbCd1234/ch4pt3r1"))
        http.routes["https://lichess.org/api/study/by/Tester"] = ok(
            """{"id":"AbCd1234","name":"Caro-Kann repertoire","createdAt":1,"updatedAt":2}
               {"id":"Zz99Yy88","name":"Rook endgames","createdAt":1,"updatedAt":2,"liked":3}""",
        )
        sync.syncStudies()
        sync.syncStudies()

        val studies = store.allResources().filter { it.kind.code == "lichess_study" }
        assertEquals(listOf("My Caro-Kann", "Rook endgames"), studies.map { it.title }.sorted())
        val imported = studies.single { it.title == "Rook endgames" }
        assertEquals("https://lichess.org/study/Zz99Yy88", imported.url)
        assertTrue(imported.isAutoImported)
        assertEquals("Bearer token", http.requests.first().second["Authorization"])
    }

    @Test
    fun studyIdsComeFromLichessStudyLinksOnly() {
        assertEquals("AbCd1234", LichessSync.studyId("https://lichess.org/study/AbCd1234"))
        assertEquals("AbCd1234", LichessSync.studyId("https://en.lichess.org/study/AbCd1234/xyz"))
        assertNull(LichessSync.studyId("https://example.com/study/AbCd1234"))
        assertNull(LichessSync.studyId(null))
    }

    @Test
    fun signedRequestsNeedALogin() = test {
        val client = LichessClient(token = { null }, http = http, gate = LichessRequestGate { now })
        try {
            client.fetchAccount()
            fail("Expected NotAuthenticated")
        } catch (expected: LichessException.NotAuthenticated) {
            assertTrue(http.requests.isEmpty())
        }
    }
}
