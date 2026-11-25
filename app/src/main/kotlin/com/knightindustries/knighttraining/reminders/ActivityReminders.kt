package com.knightindustries.knighttraining.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.KnightTrainingApplication
import com.knightindustries.knighttraining.MainActivity
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.ActivityReminder
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.store.activityReminder
import com.knightindustries.knighttraining.core.store.allActivityReminders
import com.knightindustries.knighttraining.core.store.goalTemplate
import com.knightindustries.knighttraining.core.store.planTemplate
import com.knightindustries.knighttraining.ui.activityNameRes
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * A training block's own reminders (iOS `ActivityReminderScheduler`): "Time to train" with the
 * block's title on its days, whatever the week's progress. iOS sets one repeating trigger per
 * weekday with the text fixed at that moment; here each reminder holds one inexact alarm for its
 * next occurrence, and [ActivityReminderReceiver] reads the block when it fires and sets the next
 * one. So a renamed block is announced by its new name, and a removed block (its reminders go with
 * it) or a deleted plan simply stays quiet.
 */
object ActivityReminderScheduler {
    /** Cancels whatever was set for [reminder], then sets its next occurrence if it's on. */
    fun schedule(context: Context, reminder: ActivityReminder) {
        cancel(context, reminder.id)
        val next = reminder.schedule.upcomingOccurrences(Instant.now(), 1, WeekCalendar.system()).firstOrNull() ?: return
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), alarmIntent(context, reminder.id))
    }

    /** The alarm and its intent both: a leftover intent would still read as a pending reminder. */
    fun cancel(context: Context, reminderId: UUID) {
        val pending = alarmIntent(context, reminderId)
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }

    /** After a reboot or clock change, which wipe or skew alarms. */
    suspend fun rescheduleAll(context: Context) {
        container(context).store.allActivityReminders().forEach { schedule(context, it) }
    }

    /** "Delete all data": called before the rows go, while their ids can still be read. */
    suspend fun cancelAll(context: Context) {
        container(context).store.allActivityReminders().forEach { cancel(context, it.id) }
    }

    /** One alarm per reminder, told apart by its data URI (extras don't make intents distinct). */
    private fun alarmIntent(context: Context, reminderId: UUID): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ActivityReminderReceiver::class.java)
            .setAction(ActivityReminderReceiver.ACTION_FIRE)
            .setData(Uri.parse("knighttraining://activity-reminder/$reminderId")),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal fun container(context: Context) = AppContainer.from(context.applicationContext as KnightTrainingApplication)
}

class ActivityReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.lastPathSegment?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
        val result = goAsync()
        val container = ActivityReminderScheduler.container(context)
        container.appScope.launch {
            try {
                val reminder = container.store.activityReminder(id) ?: return@launch
                if (reminder.schedule.isActive) remind(context, container, reminder)
                ActivityReminderScheduler.schedule(context, reminder)
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun remind(context: Context, container: AppContainer, reminder: ActivityReminder) {
        val block = container.store.goalTemplate(reminder.goalTemplateId) ?: return
        // A deleted plan is archived, not removed: its blocks' reminders go quiet with it.
        if (container.store.planTemplate(block.planTemplateId)?.isArchived != false) return
        val title = block.name ?: context.getString(activityNameRes(block.activityType.code)).let { base ->
            block.timeControlLabel?.let { "$base ($it)" } ?: base
        }
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.activity_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_session)
            .setContentTitle(context.getString(R.string.time_to_train))
            .setContentText(title)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(reminder.id.hashCode(), notification)
        } catch (denied: SecurityException) {
            // Notifications are off; nothing to do.
        }
    }

    companion object {
        const val ACTION_FIRE = "com.knightindustries.knighttraining.ACTIVITY_REMINDER"
        private const val CHANNEL_ID = "activity_reminders"
    }
}
