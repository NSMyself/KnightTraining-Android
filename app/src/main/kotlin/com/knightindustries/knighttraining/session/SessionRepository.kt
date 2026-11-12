package com.knightindustries.knighttraining.session

import android.content.Context
import com.knightindustries.knighttraining.core.session.PracticeSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Instant

/**
 * The one practice session in progress, if any, kept on disk on every change (iOS
 * `SessionSnapshotStore`). Elapsed time derives from timestamps, so nothing needs to run in the
 * background: the process can die mid-session and the session resumes intact, which is why there
 * is no foreground service.
 */
class SessionRepository(context: Context, private val notifier: SessionNotifier) {
    private val file = File(context.filesDir, "practice_session.json")
    private val _session = MutableStateFlow(load())
    val session: StateFlow<PracticeSession?> = _session.asStateFlow()

    init {
        // A session restored after the process died gets its notification back.
        _session.value?.let(notifier::show)
    }

    fun start(session: PracticeSession) = set(session)

    /** Applies [transform] to the current session, if there is one. */
    @Synchronized
    fun update(transform: (PracticeSession) -> PracticeSession) {
        _session.value?.let { set(transform(it)) }
    }

    fun pause() = update { it.pause(Instant.now()) }

    fun resume() = update { it.resume(Instant.now()) }

    /** Stops the clock; the session stays, waiting for its review. */
    fun stop() = update { it.stop(Instant.now()) }

    /** Saved or discarded: nothing left to recover. */
    @Synchronized
    fun clear() {
        _session.value = null
        file.delete()
        notifier.cancel()
    }

    @Synchronized
    private fun set(session: PracticeSession) {
        _session.value = session
        file.writeText(session.encode())
        notifier.show(session)
    }

    private fun load(): PracticeSession? {
        if (!file.exists()) return null
        return PracticeSession.decode(file.readText()) ?: run {
            file.delete()
            null
        }
    }
}
