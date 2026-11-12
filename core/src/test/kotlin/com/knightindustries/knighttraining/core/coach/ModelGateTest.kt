package com.knightindustries.knighttraining.core.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ModelGateTest {
    @Test
    fun theNanoVersionReadsFromAnyPlausibleName() {
        assertEquals(3, ModelGate.nanoVersion("nano-v3"))
        assertEquals(4, ModelGate.nanoVersion("Gemini Nano 4"))
        assertEquals(4, ModelGate.nanoVersion("gemini_nano_v4_preview"))
        assertEquals(2, ModelGate.nanoVersion("nano2"))
        assertNull(ModelGate.nanoVersion("gemma-3n"))
        assertNull(ModelGate.nanoVersion(null))
    }

    @Test
    fun onlyNanoThreeAndNewerRunTheCoach() {
        assertTrue(ModelGate.isSupported("nano-v3"))
        assertTrue(ModelGate.isSupported("Gemini Nano 4"))
        assertFalse(ModelGate.isSupported("nano-v2"))
        assertFalse("Unreadable stays off", ModelGate.isSupported("something new"))
    }

    @Test
    fun theLanguageIsNamedInEnglishAndOmittedForEnglish() {
        assertEquals("Portuguese (Portugal)", ModelGate.languageName(Locale.forLanguageTag("pt-PT")))
        assertEquals("Portuguese (Brazil)", ModelGate.languageName(Locale.forLanguageTag("pt-BR")))
        assertEquals("German", ModelGate.languageName(Locale.GERMAN))
        assertNull(ModelGate.languageName(Locale.US))
        assertNull(ModelGate.languageName(Locale.ENGLISH))
    }
}
