package com.knightindustries.knighttraining.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

/**
 * Port of iOS `WeekCompleteSequenceTests`: the vibration's times are measured from
 * `week_complete.wav`, not derived from it. If the file is ever replaced, these fail instead of
 * the mate's tap silently missing its hit.
 */
class WeekCompleteSoundTest {
    private class Wav(val samples: ShortArray, val rate: Int)

    /** 16-bit mono PCM, as `afconvert -f WAVE -d LEI16` writes it. */
    private fun read(): Wav {
        val bytes = File("src/main/res/raw/week_complete.wav").readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 12
        var rate = 0
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4)
            val size = buffer.getInt(offset + 4)
            when (id) {
                "fmt " -> {
                    assertEquals("mono", 1, buffer.getShort(offset + 10).toInt())
                    rate = buffer.getInt(offset + 12)
                    assertEquals("16-bit", 16, buffer.getShort(offset + 22).toInt())
                }
                "data" -> {
                    val samples = ShortArray(size / 2) { buffer.getShort(offset + 8 + it * 2) }
                    return Wav(samples, rate)
                }
            }
            offset += 8 + size + (size and 1)
        }
        error("no data chunk")
    }

    private fun Wav.peakDbfs(fromSeconds: Double, toSeconds: Double): Double {
        var peak = 0
        for (i in (fromSeconds * rate).toInt() until (toSeconds * rate).toInt()) peak = max(peak, abs(samples[i].toInt()))
        return 20 * log10(max(peak / 32768.0, 1e-9))
    }

    @Test
    fun theMateTapLandsOnTheLoudestMomentOfTheSound() {
        val wav = read()
        val peakIndex = wav.samples.indices.maxBy { abs(wav.samples[it].toInt()) }
        val peakSeconds = peakIndex.toDouble() / wav.rate
        assertEquals(WeekCompleteSequence.MATE_MILLIS / 1000.0, peakSeconds, 0.015)
    }

    /** The mate rings out to near silence before the bar is struck. */
    @Test
    fun theChimeComesAfterABeatOfSilence() {
        val wav = read()
        assertTrue("the beat before the chime should be near silent", wav.peakDbfs(1.45, 1.645) < -40)
        assertTrue("the chime should be struck right after it", wav.peakDbfs(1.65, 1.75) > -30)
    }
}
