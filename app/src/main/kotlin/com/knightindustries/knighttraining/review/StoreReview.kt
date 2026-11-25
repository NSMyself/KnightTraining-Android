package com.knightindustries.knighttraining.review

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewInfo
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.BuildConfig
import com.knightindustries.knighttraining.KnightTrainingApplication
import com.knightindustries.knighttraining.core.model.ReviewRequestPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.math.sqrt

/**
 * Asks for a Play Store review from the week-complete celebration (iOS's `requestReview()` in
 * `WeeklyPlanCelebrationScreen`), when [ReviewRequestPolicy] says so. Play's in-app review, like
 * StoreKit's, is a request: it may show nothing, and the celebration never depends on it.
 */
object StoreReview {
    /**
     * Off, like iOS's `reviewRequests` flag (default off: a kill switch until the asking is proven
     * in production). Turn on together with iOS.
     */
    const val IS_ENABLED = false

    private var foregroundSince: Instant? = null

    /** iOS's `SessionEngagementTracker`: how long the app has been in front this time. */
    fun onForeground() {
        if (foregroundSince == null) foregroundSince = Instant.now()
    }

    fun onBackground() {
        foregroundSince = null
    }

    /** Evaluates the policy for a completed week with [streak] and, if it says so, launches Play's review flow. */
    suspend fun maybeAsk(activity: Activity, streak: Int) {
        if (!IS_ENABLED) return
        val preferences = (activity.application as KnightTrainingApplication).let { AppContainer.from(it) }.preferences
        val state = preferences.reviewState()
        val now = Instant.now()
        val decision = ReviewRequestPolicy.evaluate(
            streak, state, BuildConfig.VERSION_NAME, now,
            timeInApp = foregroundSince?.let { Duration.between(it, now) } ?: Duration.ZERO,
            isCalm = { isDeviceCalm(activity) },
        )
        Log.d(TAG, "Review request decision for a $streak-week streak: $decision")
        val request = decision as? ReviewRequestPolicy.Decision.Request ?: return
        val manager = ReviewManagerFactory.create(activity)
        val info = try {
            suspendCancellableCoroutine<ReviewInfo?> { continuation ->
                manager.requestReviewFlow().addOnCompleteListener { task ->
                    continuation.resume(if (task.isSuccessful) task.result else null)
                }
            }
        } catch (failure: ReviewException) {
            null
        } ?: return
        manager.launchReviewFlow(activity, info)
        // Recorded once launched, whether or not Play actually showed anything (iOS does the same).
        preferences.setReviewState(state.asked(request.milestone, BuildConfig.VERSION_NAME, now))
    }

    /**
     * iOS's `CoreMotionCalmProbe`: samples acceleration without gravity at about 20 Hz for
     * [ReviewRequestPolicy.calmSampling] and asks the policy whether it was steady. Fails open with
     * no sensor. Magnitudes are converted to g, the unit iOS's threshold is in.
     */
    private suspend fun isDeviceCalm(context: Context): Boolean {
        val sensors = context.getSystemService(SensorManager::class.java)
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) ?: return true
        val magnitudes = mutableListOf<Double>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                magnitudes += sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, sensor, 50_000)
        try {
            delay(ReviewRequestPolicy.calmSampling.toMillis())
        } finally {
            sensors.unregisterListener(listener)
        }
        return ReviewRequestPolicy.isCalm(magnitudes.toList())
    }

    private const val TAG = "StoreReview"
}
