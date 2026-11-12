package com.knightindustries.knighttraining.core.coach

import java.util.Locale

/**
 * Which on-device models the coach trusts, and in which language it writes. The coach only runs
 * on Gemini Nano 3 or newer: Nano 2 is the smaller model, and the iOS coach's prompts were tuned
 * against a model of Nano 3's class.
 */
object ModelGate {
    const val MINIMUM_NANO_VERSION = 3

    /**
     * The Nano version in `getBaseModelName()`, whose format Google doesn't document: "nano-v3",
     * "Gemini Nano 4", "nano4" and the like all read. `null` when no version can be found, which
     * keeps the coach off rather than guessing.
     */
    fun nanoVersion(baseModelName: String?): Int? {
        val name = baseModelName ?: return null
        return Regex("nano[\\s_-]*v?(\\d+)", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun isSupported(baseModelName: String?): Boolean = (nanoVersion(baseModelName) ?: 0) >= MINIMUM_NANO_VERSION

    /**
     * The app's language as the coach's instructions name it ("Portuguese (Portugal)", "German"),
     * or `null` for English: the base instructions already are.
     */
    fun languageName(locale: Locale): String? {
        if (locale.language == "en" || locale.language.isEmpty()) return null
        return locale.getDisplayName(Locale.US).takeIf { it.isNotBlank() }
    }
}
