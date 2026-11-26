package com.knightindustries.knighttraining.coach

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelConfig
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import com.knightindustries.knighttraining.core.coach.LanguageModel
import com.knightindustries.knighttraining.core.coach.ModelGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gemini Nano through ML Kit's Prompt API (beta): the on-device model behind the weekly coach, as
 * Apple's is on iOS. Nothing leaves the phone. Runs only while the app is in the foreground
 * (AICore refuses background use), which suits a coach that runs on the review screen.
 */
class GeminiNano(
    newClient: () -> GenerativeModel = {
        Generation.getClient(generationConfig { modelConfig = ModelConfig.builder().apply { preference = ModelPreference.FULL }.build() })
    },
) : LanguageModel {
    /**
     * Made on first use, never at construction: release builds, where the coach is off, must not
     * touch ML Kit at all. Its beta setup crashed on launch in a shrunk release build (2026-11-26).
     */
    private val client: GenerativeModel by lazy(newClient)

    sealed interface Availability {
        data class Ready(val baseModelName: String) : Availability

        /** Supported, but the model isn't on the phone yet; a download was asked for. */
        data object Downloading : Availability

        data class Unavailable(val reason: String) : Availability
    }

    /**
     * Whether the coach can run now. A downloadable model starts downloading (the coach sits this
     * review out); a model older than [ModelGate.MINIMUM_NANO_VERSION], or one whose version can't
     * be read, keeps it off.
     */
    suspend fun availability(scope: CoroutineScope): Availability {
        val status = runCatching { client.checkStatus() }.getOrElse { return Availability.Unavailable("status check failed: ${it.message}") }
        return when (status) {
            FeatureStatus.AVAILABLE -> {
                val name = runCatching { client.getBaseModelName() }.getOrNull()
                // Logged so a tester can say which model their phone reports.
                Log.i(TAG, "Gemini Nano base model: $name")
                if (ModelGate.isSupported(name)) {
                    Availability.Ready(name!!)
                } else {
                    Availability.Unavailable("model \"$name\" isn't recognised as Nano ${ModelGate.MINIMUM_NANO_VERSION} or newer")
                }
            }
            FeatureStatus.DOWNLOADABLE -> {
                startDownload(scope)
                Availability.Downloading
            }
            FeatureStatus.DOWNLOADING -> Availability.Downloading
            else -> Availability.Unavailable("Gemini Nano isn't available on this device")
        }
    }

    /** Starts the download if the phone supports Nano but doesn't have it yet; does nothing otherwise. */
    suspend fun prefetch(scope: CoroutineScope) {
        val status = runCatching { client.checkStatus() }.getOrNull()
        val name = when (status) {
            FeatureStatus.AVAILABLE -> "available"
            FeatureStatus.DOWNLOADABLE -> "downloadable, asking AICore for it"
            FeatureStatus.DOWNLOADING -> "downloading"
            FeatureStatus.UNAVAILABLE -> "unavailable on this device"
            else -> "unknown ($status)"
        }
        Log.i(TAG, "Gemini Nano status at launch: $name")
        if (status == FeatureStatus.DOWNLOADABLE) startDownload(scope)
    }

    private val downloading = AtomicBoolean(false)

    /** One download at a time, however many callers ask. AICore owns the download itself. */
    private fun startDownload(scope: CoroutineScope) {
        if (!downloading.compareAndSet(false, true)) return
        scope.launch {
            try {
                client.download().collect { status ->
                    when (status) {
                        is DownloadStatus.DownloadFailed -> Log.w(TAG, "Gemini Nano download failed", status.e)
                        DownloadStatus.DownloadCompleted -> Log.i(TAG, "Gemini Nano download completed")
                        else -> Unit
                    }
                }
            } catch (failure: Exception) {
                Log.w(TAG, "Gemini Nano download failed", failure)
            } finally {
                downloading.set(false)
            }
        }
    }

    /** Instructions as a system prompt where the device takes one, else ahead of the prompt. */
    override suspend fun respond(instructions: String, prompt: String): String {
        val systemPrompt = runCatching { client.isSystemPromptAvailable() }.getOrDefault(false)
        val request = if (systemPrompt) {
            generateContentRequest(SystemInstruction(instructions), TextPart(prompt)) { configure() }
        } else {
            generateContentRequest(TextPart("$instructions\n\n$prompt")) { configure() }
        }
        return client.generateContent(request).candidates.firstOrNull()?.text.orEmpty()
    }

    // Low temperature: the coach is judged on staying grounded in the data, not on variety.
    private fun com.google.mlkit.genai.prompt.GenerateContentRequest.Builder.configure() {
        temperature = 0.2f
        maxOutputTokens = 1024
    }

    private companion object {
        const val TAG = "GeminiNano"
    }
}
