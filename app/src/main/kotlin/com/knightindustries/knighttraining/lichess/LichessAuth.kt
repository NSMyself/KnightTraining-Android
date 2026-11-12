package com.knightindustries.knighttraining.lichess

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.knightindustries.knighttraining.core.lichess.LichessClient
import com.knightindustries.knighttraining.core.lichess.LichessSync
import com.knightindustries.knighttraining.core.store.TrainingStore
import com.knightindustries.knighttraining.core.store.deleteLichessAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Lichess sign-in: OAuth 2 with PKCE (S256) in a Custom Tab, the same public client, scopes and
 * redirect as iOS (`LichessAuthService`). Lichess needs no client registration for this.
 *
 * The verifier waits in `SharedPreferences`, not memory: the system may stop the app while the
 * user is in the browser.
 */
class LichessAuth(
    private val context: Context,
    private val credentials: LichessCredentials,
    private val client: LichessClient,
    private val sync: LichessSync,
    private val store: TrainingStore,
    private val http: OkHttpClient = OkHttpClient(),
) {
    sealed interface State {
        data object Idle : State
        data object Working : State
        data object Failed : State
    }

    private val state = MutableStateFlow<State>(State.Idle)
    val signInState: StateFlow<State> = state.asStateFlow()

    private val pending = context.getSharedPreferences("lichess_oauth", Context.MODE_PRIVATE)

    fun beginSignIn(from: Context) {
        val verifier = randomToken()
        val stateParam = randomToken()
        pending.edit().putString(KEY_VERIFIER, verifier).putString(KEY_STATE, stateParam).apply()
        val url = Uri.parse("$HOST/oauth").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge(verifier))
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("state", stateParam)
            .build()
        state.value = State.Idle
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(from, url)
    }

    /** Handles the redirect: exchanges the code for a token, then creates the account row and its ratings. */
    suspend fun complete(redirect: Uri) {
        val verifier = pending.getString(KEY_VERIFIER, null)
        val expectedState = pending.getString(KEY_STATE, null)
        pending.edit().clear().apply()
        val code = redirect.getQueryParameter("code")
        // A denied consent comes back with `error=access_denied` and no code: not a failure to report.
        if (redirect.getQueryParameter("error") != null) {
            state.value = State.Idle
            return
        }
        if (code == null || verifier == null || redirect.getQueryParameter("state") != expectedState) {
            state.value = State.Failed
            return
        }
        state.value = State.Working
        val token = exchange(code, verifier)
        if (token == null) {
            state.value = State.Failed
            return
        }
        credentials.save(token)
        val user = runCatching { client.fetchAccount() }.getOrNull()
        sync.syncRatings(user)
        sync.syncActivity()
        state.value = State.Idle
    }

    /** Revokes the token (fire and forget), forgets it, and drops the account's local row and offers. */
    suspend fun signOut() {
        credentials.token()?.let { token ->
            withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(Request.Builder().url("$HOST/api/token").delete().header("Authorization", "Bearer $token").build()).execute().close()
                }
            }
        }
        credentials.clear()
        store.deleteLichessAccount()
    }

    /** Lichess revoked the login (a 401): forget it so Profile offers to connect again. */
    suspend fun loginRejected() {
        credentials.clear()
    }

    private suspend fun exchange(code: String, verifier: String): String? = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("code_verifier", verifier)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", CLIENT_ID)
            .build()
        runCatching {
            http.newCall(Request.Builder().url("$HOST/api/token").post(body).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Token exchange failed: HTTP ${response.code}")
                    return@use null
                }
                JSONObject(response.body.string()).optString("access_token").takeIf { it.isNotEmpty() }
            }
        }.onFailure { Log.w(TAG, "Token exchange failed", it) }.getOrNull()
    }

    private fun randomToken(): String = base64Url(ByteArray(32).also(SecureRandom()::nextBytes))

    private fun challenge(verifier: String): String = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    private fun base64Url(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    companion object {
        private const val TAG = "LichessAuth"
        private const val HOST = "https://lichess.org"
        private const val CLIENT_ID = "knight-training-app"
        const val REDIRECT_URI = "com.knightindustries.knighttraining://callback"
        private const val SCOPES = "puzzle:read study:read"
        private const val KEY_VERIFIER = "verifier"
        private const val KEY_STATE = "state"
    }
}
