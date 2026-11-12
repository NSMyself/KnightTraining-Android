package com.knightindustries.knighttraining.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    /** Set at the end of onboarding, not the start, so quitting halfway shows it again. */
    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data.map { it[onboardingDoneKey] ?: false }

    suspend fun completeOnboarding() {
        context.dataStore.edit { it[onboardingDoneKey] = true }
    }
}
