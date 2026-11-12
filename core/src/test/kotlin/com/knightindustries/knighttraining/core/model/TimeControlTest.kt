package com.knightindustries.knighttraining.core.model

import com.knightindustries.knighttraining.core.model.TimeControl.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of iOS `TimeControlTests`. */
class TimeControlTest {
    @Test
    fun parsesIncrementLabel() {
        assertEquals(TimeControl(15, 10), TimeControl.parse("15+10"))
    }

    @Test
    fun parsesMinutesOnlyLabel() {
        assertEquals(TimeControl(30, 0), TimeControl.parse("30 min"))
    }

    @Test
    fun rejectsUnparseableLabels() {
        // Unknown must never be reported as a guessed category.
        for (label in listOf("", "   ", "blitz", "abc+def", "0+0", "-5+2", "10+")) {
            assertNull("expected null for \"$label\"", TimeControl.parse(label))
        }
    }

    @Test
    fun labelRoundTrips() {
        for (label in listOf("3+2", "15+10", "30 min", "90+30")) {
            assertEquals(label, TimeControl.parse(label)?.label)
        }
    }

    /**
     * Every preset the picker offers must classify into the section it's shown under. Mirrors the
     * iOS `TimeControlPicker.sections`; update both together.
     */
    @Test
    fun pickerPresetsMatchTheirDisplayedSection() {
        val expected = listOf(
            "1 min" to Category.BULLET, "1+1" to Category.BULLET, "2+1" to Category.BULLET,
            "3 min" to Category.BLITZ, "3+2" to Category.BLITZ, "5 min" to Category.BLITZ,
            "10 min" to Category.RAPID, "10+5" to Category.RAPID, "15+10" to Category.RAPID,
            "30 min" to Category.CLASSICAL, "60 min" to Category.CLASSICAL, "90+30" to Category.CLASSICAL,
        )
        for ((label, category) in expected) {
            assertEquals("$label should be $category", category, TimeControl.parse(label)?.category)
        }
    }

    @Test
    fun incrementNeverChangesCategory() {
        for (increment in listOf(0, 2, 10, 25, 60)) {
            assertEquals(Category.BLITZ, TimeControl(5, increment).category)
            assertEquals(Category.RAPID, TimeControl(25, increment).category)
            assertEquals(Category.CLASSICAL, TimeControl(30, increment).category)
        }
    }

    @Test
    fun isClassicalOnlyForClassical() {
        assertTrue(TimeControl.parse("30 min")!!.isClassical)
        assertTrue(TimeControl.parse("60 min")!!.isClassical)
        assertFalse(TimeControl.parse("15+10")!!.isClassical)
        assertFalse(TimeControl.parse("3+2")!!.isClassical)
        // Correspondence is its own thing, not a very long classical game.
        assertFalse(TimeControl(24 * 60, 0).isClassical)
    }

    @Test
    fun boundariesAreInclusiveAtTheLowerEdge() {
        assertEquals(Category.BULLET, TimeControl(2).category)
        assertEquals(Category.BLITZ, TimeControl(3).category)
        assertEquals(Category.BLITZ, TimeControl(9).category)
        assertEquals(Category.RAPID, TimeControl(10).category)
        assertEquals(Category.RAPID, TimeControl(29).category)
        assertEquals(Category.CLASSICAL, TimeControl(30).category)
    }

    @Test
    fun subThirtyBaseIsNeverClassical() {
        assertEquals(Category.CLASSICAL, TimeControl.parse("30+10")?.category)
        assertEquals(Category.RAPID, TimeControl.parse("25+10")?.category)
        assertEquals(Category.RAPID, TimeControl.parse("25+25")?.category)
        assertEquals(Category.RAPID, TimeControl.parse("29+60")?.category)
    }

    @Test
    fun dailyBoundary() {
        assertEquals(Category.CLASSICAL, TimeControl(23 * 60).category)
        assertEquals(Category.CLASSICAL, TimeControl(24 * 60 - 1).category)
        assertEquals(Category.DAILY, TimeControl(24 * 60).category)
        assertEquals(Category.DAILY, TimeControl(3 * 24 * 60).category)
    }
}
