package com.knightindustries.knighttraining.lichess

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.MainActivity
import kotlinx.coroutines.launch

/**
 * Receives Lichess's OAuth redirect, hands it to [LichessAuth] on the app scope (so it outlives
 * this activity), and returns to the app. `CLEAR_TOP` also closes the Custom Tab, which sits above
 * `MainActivity` in the same task.
 */
class LichessCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { redirect ->
            val container = AppContainer.from(application)
            container.appScope.launch { container.lichessAuth.complete(redirect) }
        }
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
