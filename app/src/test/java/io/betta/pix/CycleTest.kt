package io.betta.pix

import org.junit.Assert.assertEquals
import org.junit.Test

class CycleTest {
    private val values = listOf(5, 10, 15, 20)

    @Test
    fun `steps along and wraps at either end`() {
        assertEquals(15, cycle(values, 10, 1))
        assertEquals(5, cycle(values, 20, 1))
        assertEquals(20, cycle(values, 5, -1))
    }

    @Test
    fun `a value that's no longer offered starts from the first`() {
        assertEquals(10, cycle(values, 7, 1))
    }

    @Test
    fun `brightness steps in tenths and stops at 20 and 100 percent`() {
        assertEquals(0.8f, ScreensaverSettings.stepBrightness(0.7f, 1))
        assertEquals(1f, ScreensaverSettings.stepBrightness(1f, 1))
        assertEquals(0.2f, ScreensaverSettings.stepBrightness(0.2f, -1))
    }

    @Test
    fun `brightness lands on whole tenths however many steps it took`() {
        var brightness = 0.7f
        repeat(3) { brightness = ScreensaverSettings.stepBrightness(brightness, 1) }
        assertEquals(1f, brightness)
        assertEquals(0.4f, ScreensaverSettings.stepBrightness(0.40000004f, 0))
    }

    @Test
    fun `durations read in seconds under a minute and minutes from one`() {
        assertEquals("10 s", ScreensaverSettings.duration(10_000L))
        assertEquals("2 min", ScreensaverSettings.duration(120_000L))
    }
}
