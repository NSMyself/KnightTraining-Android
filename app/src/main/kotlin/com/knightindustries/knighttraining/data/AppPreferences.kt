package com.knightindustries.knighttraining.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.knightindustries.knighttraining.core.coach.WeakThemeStreak
import com.knightindustries.knighttraining.core.model.ReminderSchedule
import com.knightindustries.knighttraining.core.net.CatalogSync
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant

private val Context.dataStore by preferencesDataStore(name = "preferences")

/** Small app settings, the counterpart of iOS's `UserDefaults` keys. */
class AppPreferences(private val context: Context) {
    private val firstLaunchKey = longPreferencesKey("first_launch")
    private val onboardingDoneKey = booleanPreferencesKey("has_completed_onboarding")
    private val homeLayoutKey = stringPreferencesKey("home_layout")

    /**
     * Home's tile ids in order, or `null` for the default. Lives here, not in the database, so
     * "Delete all data" keeps it (iOS keeps it in `UserDefaults` for the same reason).
     */
    val homeLayout: Flow<List<String>?> = context.dataStore.data.map { prefs ->
        prefs[homeLayoutKey]?.let { saved -> if (saved.isEmpty()) emptyList() else saved.split(',') }
    }

    suspend fun setHomeLayout(ids: List<String>) {
        context.dataStore.edit { it[homeLayoutKey] = ids.joinToString(",") }
    }

    suspend fun resetHomeLayout() {
        context.dataStore.edit { it.remove(homeLayoutKey) }
    }

    private val weakThemeStreakKey = stringPreferencesKey("weak_theme_streak")
    private val lastCoachingSuggestionKey = longPreferencesKey("last_coaching_suggestion")

    /** The weekly coach's weak-theme streak, carried from one review to the next (iOS `WeakThemeStreak`). */
    suspend fun weakThemeStreak(): WeakThemeStreak? =
        context.dataStore.data.first()[weakThemeStreakKey]?.let { runCatching { Json.decodeFromString(WeakThemeStreak.serializer(), it) }.getOrNull() }

    suspend fun setWeakThemeStreak(streak: WeakThemeStreak?) {
        context.dataStore.edit { prefs ->
            if (streak == null) prefs.remove(weakThemeStreakKey) else prefs[weakThemeStreakKey] = Json.encodeToString(WeakThemeStreak.serializer(), streak)
        }
    }

    /** When a "work with a coach" tip was last on screen: one every three months at most. */
    suspend fun lastCoachingSuggestion(): Instant? = context.dataStore.data.first()[lastCoachingSuggestionKey]?.let(Instant::ofEpochMilli)

    suspend fun setLastCoachingSuggestion(at: Instant) {
        context.dataStore.edit { it[lastCoachingSuggestionKey] = at.toEpochMilli() }
    }

    /**
     * When the app was first opened: the anchor for the first-week grace period
     * (`WeeklyPlan.effectiveWeekStart`). Stamped once and never moved.
     */
    suspend fun firstLaunch(): Instant {
        context.dataStore.data.first()[firstLaunchKey]?.let { return Instant.ofEpochMilli(it) }
        val now = Instant.now()
        context.dataStore.edit { if (it[firstLaunchKey] == null) it[firstLaunchKey] = now.toEpochMilli() }
        return Instant.ofEpochMilli(context.dataStore.data.first()[firstLaunchKey]!!)
    }

    private val lastReviewedWeekKey = stringPreferencesKey("last_weekly_review_week_start")

    /** The week whose review was last saved, so launch offers each week's review once. */
    suspend fun lastReviewedWeekStart(): LocalDate? = context.dataStore.data.first()[lastReviewedWeekKey]?.let(LocalDate::parse)

    suspend fun setLastReviewedWeekStart(weekStart: LocalDate) {
        context.dataStore.edit { it[lastReviewedWeekKey] = weekStart.toString() }
    }

    /**
     * The catalog's last ETag. Versioned like iOS's key: bump the suffix when the app starts
     * storing a catalog field it ignored before, or a 304 would hide the field forever.
     */
    val catalogEtags = object : CatalogSync.EtagStore {
        private val key = stringPreferencesKey("catalog_etag_v1")

        override suspend fun get(): String? = context.dataStore.data.first()[key]

        override suspend fun set(etag: String) {
            context.dataStore.edit { it[key] = etag }
        }
    }

    private val reminderEnabledKey = booleanPreferencesKey("reminder_enabled")
    private val reminderDaysKey = stringPreferencesKey("reminder_days")
    private val reminderTimeKey = stringPreferencesKey("reminder_time")

    /** When the user is willing to be nudged. Off until they opt in. */
    val reminderSchedule: Flow<ReminderSchedule> = context.dataStore.data.map { prefs ->
        val time = prefs[reminderTimeKey]?.split(":")?.mapNotNull(String::toIntOrNull)
        ReminderSchedule(
            isEnabled = prefs[reminderEnabledKey] ?: false,
            weekdays = prefs[reminderDaysKey]?.split(",")?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }?.toSet()
                ?: ReminderSchedule.EVERY_DAY,
            hour = time?.getOrNull(0) ?: ReminderSchedule.DEFAULT.hour,
            minute = time?.getOrNull(1) ?: ReminderSchedule.DEFAULT.minute,
        )
    }

    suspend fun setReminderSchedule(schedule: ReminderSchedule) {
        context.dataStore.edit {
            it[reminderEnabledKey] = schedule.isEnabled
            it[reminderDaysKey] = schedule.weekdays.joinToString(",") { day -> day.name }
            it[reminderTimeKey] = "${schedule.hour}:${schedule.minute}"
        }
    }

    /** Set at the end of onboarding, not the start, so quitting halfway shows it again. */
    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data.map { it[onboardingDoneKey] ?: false }

    suspend fun completeOnboarding() = setOnboardingCompleted(true)

    /** `false` shows onboarding again: tests, and a future "replay intro" setting. */
    suspend fun setOnboardingCompleted(done: Boolean) {
        context.dataStore.edit { it[onboardingDoneKey] = done }
    }
}
