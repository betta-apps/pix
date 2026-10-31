package io.betta.pix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlackBarsTest {
    private val width = 480
    private val height = 270

    @Test
    fun `a 4-3 still in a 16-9 frame has bars`() {
        val bar = (width - height * 4 / 3) / 2
        assertTrue(hasBlackBars(width, height) { x, _ -> if (x < bar || x >= width - bar) 8 else 120 })
    }

    @Test
    fun `a letterboxed still has bars`() {
        assertTrue(hasBlackBars(width, height) { _, y -> if (y < 30 || y >= height - 30) 5 else 140 })
    }

    @Test
    fun `a portrait poster on a black panel has bars`() {
        // Close Encounters of the Third Kind's backdrop: the poster on the left, the right 44% black.
        assertTrue(hasBlackBars(width, height) { x, _ -> if (x >= width * 56 / 100) 0 else 33 + x % 190 })
    }

    @Test
    fun `a dark scene that stays dark further in has none`() {
        assertFalse(hasBlackBars(width, height) { x, _ -> if (x in 200..280) 90 else 15 })
    }

    @Test
    fun `a dark edge on one side only has none`() {
        assertFalse(hasBlackBars(width, height) { x, _ -> if (x < 40) 6 else 120 })
    }

    @Test
    fun `a full-frame picture has none`() {
        assertFalse(hasBlackBars(width, height) { x, y -> 40 + (x + y) % 160 })
    }
}
