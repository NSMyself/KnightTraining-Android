package com.knightindustries.knighttraining.session

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.knightindustries.knighttraining.MainActivity
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.session.PracticeSession
import com.knightindustries.knighttraining.ui.activityNameRes
import com.knightindustries.knighttraining.ui.clockText
import java.time.Instant

/**
 * The practice timer's ongoing notification: Android's counterpart of the iOS Live Activity. The
 * system draws the running clock (a chronometer anchored to the segment's start), so nothing in
 * the app has to tick. On Android 16 it asks to be promoted to a Live Update.
 */
class SessionNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_session), NotificationManager.IMPORTANCE_LOW)
            .apply { description = context.getString(R.string.notification_channel_session_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(session: PracticeSession) {
        val now = Instant.now()
        val title = session.attribution.activityTypeCode?.let { context.getString(activityNameRes(it)) }
            ?: context.getString(R.string.general_practice)
        val segmentSeconds = session.segmentElapsedSeconds(now)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_session)
            .setContentTitle(title)
            .setContentIntent(openApp(ACTION_OPEN))
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)

        when {
            !session.isRunning -> builder
                .setContentText(context.getString(R.string.notification_session_stopped))
                .setOngoing(false)
                .setAutoCancel(true)
            session.isPaused -> builder
                .setContentText(context.getString(R.string.notification_session_paused, clockText(segmentSeconds)))
                .setOngoing(true)
                .addAction(0, context.getString(R.string.resume), broadcast(SessionActionReceiver.ACTION_RESUME))
                .addAction(0, context.getString(R.string.stop), openApp(ACTION_STOP))
            else -> builder
                .setContentText(context.getString(R.string.notification_session_running))
                .setOngoing(true)
                .setUsesChronometer(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() - segmentSeconds * 1000L)
                .setRequestPromotedOngoing(true)
                .addAction(0, context.getString(R.string.pause), broadcast(SessionActionReceiver.ACTION_PAUSE))
                .addAction(0, context.getString(R.string.stop), openApp(ACTION_STOP))
        }

        try {
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (denied: SecurityException) {
            // Notifications are off: the session still runs, the app shows it.
        }
    }

    fun cancel() = manager.cancel(NOTIFICATION_ID)

    private fun broadcast(action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, SessionActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Stop opens the app on the review: it always needs one before anything is saved. */
    private fun openApp(action: String): PendingIntent = PendingIntent.getActivity(
        context, action.hashCode(),
        Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_ID = "practice_session"
        const val NOTIFICATION_ID = 1
        const val ACTION_OPEN = "com.knightindustries.knighttraining.OPEN_SESSION"
        const val ACTION_STOP = "com.knightindustries.knighttraining.STOP_SESSION"
    }
}
