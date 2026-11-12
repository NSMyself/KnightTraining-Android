package com.knightindustries.knighttraining.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.knightindustries.knighttraining.core.net.CatalogSync
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

private val Context.dataStore by preferencesDataStore(name = "preferences")

/** Small app settings, the counterpart of iOS's `UserDefaults` keys. */
class AppPreferences(private val context: Context) {
    private val firstLaunchKey = longPreferencesKey("first_launch")
    private val onboardingDoneKey = booleanPreferencesKey("has_completed_onboarding")

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

    /** Set at the end of onboarding, not the start, so quitting halfway shows it again. */
    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data.map { it[onboardingDoneKey] ?: false }

    suspend fun completeOnboarding() = setOnboardingCompleted(true)

    /** `false` shows onboarding again: tests, and a future "replay intro" setting. */
    suspend fun setOnboardingCompleted(done: Boolean) {
        context.dataStore.edit { it[onboardingDoneKey] = done }
    }
}
