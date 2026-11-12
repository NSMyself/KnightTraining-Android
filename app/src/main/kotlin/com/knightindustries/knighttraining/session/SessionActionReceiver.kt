package com.knightindustries.knighttraining.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.KnightTrainingApplication

/** Pause and Resume from the notification, with or without the app open. */
class SessionActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessions = AppContainer.from(context.applicationContext as KnightTrainingApplication).sessions
        when (intent.action) {
            ACTION_PAUSE -> sessions.pause()
            ACTION_RESUME -> sessions.resume()
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.knightindustries.knighttraining.PAUSE_SESSION"
        const val ACTION_RESUME = "com.knightindustries.knighttraining.RESUME_SESSION"
    }
}
