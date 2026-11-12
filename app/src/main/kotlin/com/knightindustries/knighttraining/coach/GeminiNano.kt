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

/**
 * Gemini Nano through ML Kit's Prompt API (beta): the on-device model behind the weekly coach, as
 * Apple's is on iOS. Nothing leaves the phone. Runs only while the app is in the foreground
 * (AICore refuses background use), which suits a coach that runs on the review screen.
 */
class GeminiNano(
    private val client: GenerativeModel = Generation.getClient(
        generationConfig { modelConfig = ModelConfig.builder().apply { preference = ModelPreference.FULL }.build() },
    ),
) : LanguageModel {
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
                if (ModelGate.isSupported(name)) Availability.Ready(name!!) else Availability.Unavailable("model $name is older than Nano ${ModelGate.MINIMUM_NANO_VERSION} or unreadable")
            }
            FeatureStatus.DOWNLOADABLE -> {
                scope.launch {
                    runCatching {
                        client.download().collect { if (it is DownloadStatus.DownloadFailed) Log.w(TAG, "Gemini Nano download failed", it.e) }
                    }
                }
                Availability.Downloading
            }
            FeatureStatus.DOWNLOADING -> Availability.Downloading
            else -> Availability.Unavailable("Gemini Nano isn't available on this device")
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
