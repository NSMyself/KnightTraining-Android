package com.knightindustries.knighttraining.feedback

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi

/**
 * The week-complete moment's vibration (iOS `WeekCompleteSequence`): one tap per hit in
 * `week_complete.wav`, three trades, the mate, then the chime's two strikes. The times are measured
 * from the file, not chosen; `WeekCompleteSoundTest` fails if the file moves under them.
 *
 * iOS plays the sound inside its haptic pattern so the two share a clock. Android has no such
 * pairing: both start together, which holds to within the sound pool's few milliseconds of latency.
 */
class WeekCompleteSequence(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    /** Plays the taps; `false` when the device can't (no vibrator, or none that can vary its strength). */
    fun vibrate(): Boolean {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return false
        val effect = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) composition(vibrator) else null)
            ?: waveform(vibrator)
            ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Touch usage: the system's touch-feedback switch silences it, like every other haptic here.
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
        }
        return true
    }

    /** Haptic primitives, placed at the measured times. Needs API 31 to know each primitive's length. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun composition(vibrator: Vibrator): VibrationEffect? {
        val mate = if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) VibrationEffect.Composition.PRIMITIVE_THUD
        else VibrationEffect.Composition.PRIMITIVE_CLICK
        val hits = HITS.map { hit ->
            val primitive = when (hit.kind) {
                Hit.Kind.Move -> VibrationEffect.Composition.PRIMITIVE_CLICK
                Hit.Kind.Mate -> mate
                Hit.Kind.Chime -> VibrationEffect.Composition.PRIMITIVE_TICK
            }
            primitive to hit
        }
        if (!vibrator.areAllPrimitivesSupported(*hits.map { it.first }.distinct().toIntArray())) return null
        val durations = vibrator.getPrimitiveDurations(*hits.map { it.first }.toIntArray())
        val composition = VibrationEffect.startComposition()
        // Each primitive's delay counts from the end of the one before, so subtract what's elapsed.
        var cursor = 0
        hits.forEachIndexed { index, (primitive, hit) ->
            val delay = (hit.atMillis - cursor).coerceAtLeast(0)
            composition.addPrimitive(primitive, hit.intensity, delay)
            cursor += delay + durations[index]
        }
        return composition.compose()
    }

    /** Short pulses of varying strength, for vibrators without primitives. */
    private fun waveform(vibrator: Vibrator): VibrationEffect? {
        if (!vibrator.hasAmplitudeControl()) return null
        val timings = mutableListOf<Long>()
        val amplitudes = mutableListOf<Int>()
        var cursor = 0L
        for (hit in HITS) {
            timings += (hit.atMillis - cursor).coerceAtLeast(0)
            amplitudes += 0
            timings += PULSE_MILLIS
            amplitudes += (hit.intensity * 255).toInt().coerceIn(1, 255)
            cursor = hit.atMillis + PULSE_MILLIS
        }
        return VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), -1)
    }

    private data class Hit(val atMillis: Int, val intensity: Float, val kind: Kind) {
        enum class Kind { Move, Mate, Chime }
    }

    companion object {
        /** When the mating move lands: the confetti waits for it, so the burst arrives with the payoff. */
        const val MATE_MILLIS = 1_305L

        private const val PULSE_MILLIS = 20L

        // Each hit lands 5 ms after its nominal time: every piece sample keeps a 5 ms pre-roll.
        private val HITS = listOf(
            Hit(5, 0.45f, Hit.Kind.Move),
            Hit(555, 0.45f, Hit.Kind.Move),
            Hit(805, 0.35f, Hit.Kind.Move), // the quick recapture: lighter, as a real one is
            Hit(MATE_MILLIS.toInt(), 1f, Hit.Kind.Mate),
            Hit(1_655, 0.3f, Hit.Kind.Chime), // the chime's two strikes, G4 then D5
            Hit(1_805, 0.2f, Hit.Kind.Chime),
        )
    }
}
