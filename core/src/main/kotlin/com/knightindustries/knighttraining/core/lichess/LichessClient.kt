package com.knightindustries.knighttraining.core.lichess

import com.knightindustries.knighttraining.core.net.HttpGet
import com.knightindustries.knighttraining.core.net.OkHttpGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.time.Instant

sealed class LichessException(message: String) : Exception(message) {
    class NotAuthenticated : LichessException("Not signed in to Lichess")
    class Network : LichessException("Couldn't reach Lichess")
    class Http(val status: Int, val body: String) : LichessException("Lichess HTTP $status")
    class Decoding(cause: Throwable) : LichessException("Unexpected Lichess payload: ${cause.message}")
    class RateLimited(val until: Instant) : LichessException("Lichess rate limit in effect until $until")
}

/**
 * Lichess's own rule: one request in flight at a time, and a full minute's pause after any 429. One
 * instance for the whole process ([shared]), since the limit is per account/IP, not per client.
 * [Mutex] is fair, so waiting callers go in arrival order.
 */
class LichessRequestGate(private val now: () -> Instant = Instant::now) {
    private val mutex = Mutex()
    private var cooldownUntil: Instant? = null

    suspend fun <T> perform(request: suspend () -> T): T = mutex.withLock {
        cooldownUntil?.let { if (it.isAfter(now())) throw LichessException.RateLimited(it) }
        try {
            request()
        } catch (failure: LichessException.Http) {
            if (failure.status == 429) cooldownUntil = now().plusSeconds(60)
            throw failure
        }
    }

    companion object {
        val shared = LichessRequestGate()
    }
}

/**
 * The Lichess API, separate from sign-in (iOS's hard-won lesson: cleaner 401 recovery). Every
 * request goes through the [gate].
 *
 * @param token the stored access token, or `null` when signed out.
 * @param onUnauthorized called on a 401 to a signed request: the login is no longer valid.
 */
class LichessClient(
    private val token: suspend () -> String?,
    private val onUnauthorized: suspend () -> Unit = {},
    private val http: HttpGet = OkHttpGet(),
    private val gate: LichessRequestGate = LichessRequestGate.shared,
    private val baseUrl: String = "https://lichess.org",
) {
    suspend fun fetchAccount(): LichessUser = getJson("/api/account", LichessUser.serializer(), auth = Auth.REQUIRED)

    suspend fun fetchRatingHistory(username: String): List<LichessRatingHistory> =
        getJson("/api/user/${segment(username)}/rating-history", ListSerializer(LichessRatingHistory.serializer()), auth = Auth.NONE)

    suspend fun fetchPuzzleActivity(since: Instant?, max: Int = 50): List<LichessPuzzleActivity> {
        val query = buildList {
            add("max" to "$max")
            since?.let { add("since" to "${it.toEpochMilli()}") }
        }
        return getNdjson("/api/puzzle/activity", query, LichessPuzzleActivity.serializer(), auth = Auth.REQUIRED)
    }

    suspend fun fetchPuzzleDashboard(days: Int = 30): LichessPuzzleDashboard =
        getJson("/api/puzzle/dashboard/$days", LichessPuzzleDashboard.serializer(), auth = Auth.REQUIRED)

    suspend fun fetchStormDashboard(username: String, days: Int = 2): LichessStormDashboard =
        getJson("/api/storm/dashboard/${segment(username)}", LichessStormDashboard.serializer(), listOf("days" to "$days"), Auth.NONE)

    suspend fun fetchActivity(username: String): List<LichessActivityFeedDay> =
        getJson("/api/user/${segment(username)}/activity", ListSerializer(LichessActivityFeedDay.serializer()), auth = Auth.NONE)

    /** The user's studies, including private ones (`study:read`, which every Android login asks for). */
    suspend fun fetchStudies(username: String): List<LichessStudy> =
        getNdjson("/api/study/by/${segment(username)}", emptyList(), LichessStudy.serializer(), auth = Auth.REQUIRED)

    /** Bullet and correspondence never enter the pipeline: filtered server-side by `perfType`. */
    suspend fun fetchGames(username: String, since: Instant, max: Int = 50): List<LichessGame> = getNdjson(
        "/api/games/user/${segment(username)}",
        listOf(
            "since" to "${since.toEpochMilli()}",
            "perfType" to "rapid,classical,blitz",
            "max" to "$max",
            "sort" to "dateAsc",
            "pgnInJson" to "false",
            "moves" to "false",
            "tags" to "false",
            "clocks" to "false",
        ),
        LichessGame.serializer(),
        auth = Auth.OPTIONAL,
    )

    private enum class Auth { NONE, OPTIONAL, REQUIRED }

    private suspend fun <T> getJson(
        path: String,
        deserializer: DeserializationStrategy<T>,
        query: List<Pair<String, String>> = emptyList(),
        auth: Auth,
    ): T {
        val body = get(path, query, "application/json", auth)
        return try {
            json.decodeFromString(deserializer, body)
        } catch (failure: SerializationException) {
            throw LichessException.Decoding(failure)
        } catch (failure: IllegalArgumentException) {
            throw LichessException.Decoding(failure)
        }
    }

    /** Newline-delimited JSON: one value per line; a line that doesn't decode is skipped. */
    private suspend fun <T> getNdjson(
        path: String,
        query: List<Pair<String, String>>,
        deserializer: DeserializationStrategy<T>,
        auth: Auth,
    ): List<T> = get(path, query, "application/x-ndjson", auth)
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line -> runCatching { json.decodeFromString(deserializer, line) }.getOrNull() }
        .toList()

    private suspend fun get(path: String, query: List<Pair<String, String>>, accept: String, auth: Auth): String {
        val bearer = if (auth == Auth.NONE) null else token()
        if (auth == Auth.REQUIRED && bearer == null) throw LichessException.NotAuthenticated()
        val queryString = if (query.isEmpty()) "" else query.joinToString("&", prefix = "?") { (k, v) -> "$k=${encode(v)}" }
        val headers = buildMap {
            put("Accept", accept)
            bearer?.let { put("Authorization", "Bearer $it") }
        }
        val response = try {
            gate.perform {
                val response = http.get(baseUrl + path + queryString, headers) ?: throw LichessException.Network()
                if (!response.isSuccess) throw LichessException.Http(response.status, response.body)
                response
            }
        } catch (failure: LichessException.Http) {
            // A rejected token means the login is gone (revoked on lichess.org, or expired).
            if (failure.status == 401 && bearer != null) onUnauthorized()
            throw failure
        }
        return response.body
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

        private fun segment(value: String): String = encode(value).replace("+", "%20")
    }
}
