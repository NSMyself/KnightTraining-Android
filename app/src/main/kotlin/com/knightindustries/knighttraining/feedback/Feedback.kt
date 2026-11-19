package com.knightindustries.knighttraining.feedback

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The app's one vocabulary of tactile and audible feedback (iOS `FeedbackService`). Call sites
 * name the *moment*, never a haptic type or a sound, so the same moment always feels the same.
 *
 * Rules, carried over from iOS:
 * - **Acknowledge, don't decorate.** Only deliberate actions: no navigation, no sheets, nothing
 *   for discarding, deleting or errors.
 * - **Haptics do most of the work; sound is for accomplishments only.** Never a sound for a tap.
 * - **Never while the timer runs on its own.** Nothing here fires from a tick.
 * - **Fire at the tap, from the UI.** The ViewModel's pause/stop also run from the notification's
 *   buttons, which aren't a tap the user just felt in the app.
 *
 * Haptic types are Compose's, which pick the closest primitive the device has (`CONFIRM` and the
 * segment ticks need API 30/34) and honour the system's touch-feedback switch.
 */
enum class FeedbackEvent(val haptic: HapticFeedbackType?, val sound: FeedbackSound? = null) {
    SessionStarted(HapticFeedbackType.Confirm),
    SessionPaused(HapticFeedbackType.ToggleOff),
    SessionResumed(HapticFeedbackType.ToggleOn),
    SessionStopped(HapticFeedbackType.LongPress),
    /** A practice session (or a logged or edited one) was written. */
    SessionSaved(HapticFeedbackType.Confirm),
    /** "Change" applied mid-session: a new segment begins. */
    ActivityChanged(HapticFeedbackType.ContextClick),
    /** Toggling a choice in a multi-select list. */
    SelectionChanged(HapticFeedbackType.SegmentTick),
    /** A plan committed to this week, or a new plan saved. */
    PlanSaved(HapticFeedbackType.Confirm),
    /** A detected Lichess batch accepted into training history. */
    LichessActivityTracked(HapticFeedbackType.Confirm),
    /**
     * A weekly goal crossed its target, unless the same write completed the week. No haptic: it
     * arrives a beat after the save that caused it, which already confirmed.
     */
    GoalCompleted(null, FeedbackSound.Piece),
    /** A milestone marked done by a tap. */
    MilestoneCompleted(HapticFeedbackType.Confirm, FeedbackSound.Piece),
    /** A Home tile picked up to rearrange it. */
    HomeTileLifted(HapticFeedbackType.GestureThresholdActivate),
    /** A dragged Home tile moved past another. */
    HomeTileSlotChanged(HapticFeedbackType.SegmentFrequentTick),
}

/** Converted from iOS's CAFs to 16-bit 48 kHz WAV, so timings measured on the files still hold. */
enum class FeedbackSound(@param:RawRes val res: Int, val volume: Float) {
    /** One chess piece set down. */
    Piece(R.raw.piece, 0.5f),
    /** Three trades, the mate, a beat, then a wooden-bar chime. */
    WeekComplete(R.raw.week_complete, 0.7f),
}

/** Plays sounds and holds the two switches. One per process, in [AppContainer]. */
class Feedback(context: Context, private val preferences: AppPreferences, private val scope: CoroutineScope) {
    val soundsEnabled: StateFlow<Boolean> = preferences.soundsEnabled.stateIn(scope, SharingStarted.Eagerly, true)
    val hapticsEnabled: StateFlow<Boolean> = preferences.hapticsEnabled.stateIn(scope, SharingStarted.Eagerly, true)

    // Sonification: the system/notification sound stream, so the ringer's silent and vibrate
    // modes mute it, and no audio focus, so a course video playing alongside isn't interrupted
    // (iOS's `.ambient` session).
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    // Loaded up front: they're small, and a first play shouldn't lag behind its moment.
    private val soundIds: Map<FeedbackSound, Int> = FeedbackSound.entries.associateWith { pool.load(context, it.res, 1) }

    /** Plays [event]'s sound, if it has one and sounds are on. Haptics go through [rememberFeedback]. */
    fun playSound(event: FeedbackEvent) {
        event.sound?.let(::playSound)
    }

    fun playSound(sound: FeedbackSound) {
        if (!soundsEnabled.value) return
        val id = soundIds.getValue(sound)
        pool.play(id, sound.volume, sound.volume, 1, 0, 1f)
    }

    fun setSoundsEnabled(on: Boolean) {
        scope.launch { preferences.setSoundsEnabled(on) }
    }

    fun setHapticsEnabled(on: Boolean) {
        scope.launch { preferences.setHapticsEnabled(on) }
    }

    /** Plays [event] in full: its haptic through [haptics] and its sound. */
    fun play(event: FeedbackEvent, haptics: HapticFeedback) {
        if (hapticsEnabled.value) event.haptic?.let(haptics::performHapticFeedback)
        playSound(event)
    }
}

/** `feedback(FeedbackEvent.SessionSaved)`, from a click handler. */
fun interface FeedbackPlayer {
    operator fun invoke(event: FeedbackEvent)
}

@Composable
fun rememberFeedback(): FeedbackPlayer {
    val haptics = LocalHapticFeedback.current
    val application = LocalContext.current.applicationContext as Application
    return remember(haptics) {
        val feedback = AppContainer.from(application).feedback
        FeedbackPlayer { event -> feedback.play(event, haptics) }
    }
}
