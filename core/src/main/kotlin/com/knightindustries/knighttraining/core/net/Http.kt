package com.knightindustries.knighttraining.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class HttpResponse(val status: Int, val body: String, val headers: Map<String, String>) {
    val isSuccess: Boolean get() = status in 200..299
}

/**
 * The one way `core` reaches the network: a GET returning text, `null` on any transport failure.
 * Every caller treats `null` as "carry on without it" (silent degradation, iOS AGENTS.md §2).
 */
fun interface HttpGet {
    suspend fun get(url: String, headers: Map<String, String>): HttpResponse?
}

class OkHttpGet(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) : HttpGet {
    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse? = withContext(Dispatchers.IO) {
        val request = runCatching {
            Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        }.getOrNull() ?: return@withContext null
        try {
            client.newCall(request).execute().use { response ->
                HttpResponse(response.code, response.body.string(), response.headers.toMap().mapKeys { it.key.lowercase() })
            }
        } catch (failure: IOException) {
            null
        }
    }
}
