package com.knightindustries.knighttraining.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.KnightTrainingApplication
import com.knightindustries.knighttraining.MainActivity
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.TrainingReminderDecision
import com.knightindustries.knighttraining.core.model.TrainingReminderPolicy
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.store.weeklySurface
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * "Remind me only when I'm behind." Unlike iOS, where a notification's text is fixed when it's
 * scheduled (so every data change rebuilds the whole set), Android runs code when the alarm
 * fires: [ReminderReceiver] asks [TrainingReminderPolicy] right then and stays silent if the week
 * is done or the user already trained today. So there's only ever one alarm: the next occurrence.
 * Inexact on purpose: a few minutes either way doesn't matter, and exact alarms need a permission.
 */
object ReminderScheduler {
    private const val REQUEST_CODE = 42

    suspend fun reschedule(context: Context) {
        val container = AppContainer.from(context.applicationContext as KnightTrainingApplication)
        val schedule = container.preferences.reminderSchedule.first()
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        alarms.cancel(pending)
        val next = schedule.upcomingOccurrences(Instant.now(), 1, WeekCalendar.system()).firstOrNull() ?: return
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), pending)
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE, Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** The alarm, plus reboots and clock changes, which wipe or skew alarms. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val container = AppContainer.from(context.applicationContext as KnightTrainingApplication)
        container.appScope.launch {
            try {
                if (intent.action == ACTION_FIRE) {
                    remindIfBehind(context, container)
                } else {
                    // A reboot or clock change: the blocks' own reminders need their alarms back too.
                    ActivityReminderScheduler.rescheduleAll(context)
                }
                ReminderScheduler.reschedule(context)
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun remindIfBehind(context: Context, container: AppContainer) {
        val now = Instant.now()
        val calendar = WeekCalendar.system()
        val surface = container.store.weeklySurface(now, container.preferences.firstLaunch(), calendar)
        val plan = surface.plan
        val decision = TrainingReminderPolicy.decide(
            hasCommittedPlan = plan != null,
            totalGoals = plan?.goalCount ?: 0,
            completedGoals = plan?.completedGoalCount ?: 0,
            trainedToday = surface.trainedToday,
            now = now,
            calendar = calendar,
        )
        val (title, body) = when (decision) {
            TrainingReminderDecision.None -> return
            TrainingReminderDecision.CommitWeeklyPlan ->
                context.getString(R.string.reminder_new_week_title) to context.getString(R.string.reminder_new_week_body)
            is TrainingReminderDecision.TrainingNudge -> {
                val days = if (decision.daysLeftInWeek == 1) {
                    context.getString(R.string.reminder_last_day)
                } else {
                    context.resources.getQuantityString(R.plurals.reminder_days_left, decision.daysLeftInWeek, decision.daysLeftInWeek)
                }
                if (decision.completedGoals == 0) {
                    context.getString(R.string.reminder_nothing_logged_title) to context.getString(R.string.reminder_nothing_logged_body, days)
                } else {
                    context.getString(R.string.reminder_progress_title, decision.completedGoals, decision.totalGoals) to
                        context.getString(R.string.reminder_progress_body, days)
                }
            }
        }
        post(context, title, body)
    }

    private fun post(context: Context, title: String, body: String) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_session)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (denied: SecurityException) {
            // Notifications are off; nothing to do.
        }
    }

    companion object {
        const val ACTION_FIRE = "com.knightindustries.knighttraining.REMINDER"
        private const val CHANNEL_ID = "training_reminders"
        private const val NOTIFICATION_ID = 2
    }
}
