package io.betta.pix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurTest {
    private fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun level(pixel: Int) = pixel and 0xFF

    @Test
    fun `a plain colour stays as it is, edges included`() {
        val plain = IntArray(20 * 10) { grey(120) }
        assertTrue(boxBlur(plain, 20, 10, 3).all { it == grey(120) })
    }

    @Test
    fun `a hard edge becomes a smooth ramp, with no steps back`() {
        // Black on the left, white on the right.
        val edge = IntArray(40 * 4) { if (it % 40 < 20) grey(0) else grey(255) }
        val row = boxBlur(edge, 40, 4, 3).take(40).map(::level)
        assertTrue(row.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(row[19] in 1..254 && row[20] in 1..254)
        assertEquals(0, row.first())
        assertEquals(255, row.last())
    }

    @Test
    fun `a bright square spreads the same way up, down and across`() {
        val square = IntArray(31 * 31) { if (it / 31 in 13..17 && it % 31 in 13..17) grey(255) else grey(0) }
        val out = boxBlur(square, 31, 31, 3).map(::level)
        for (d in 1..8) {
            assertEquals(out[15 * 31 + 15 - d].toDouble(), out[(15 - d) * 31 + 15].toDouble(), 1.0)
            assertEquals(out[15 * 31 + 15 + d].toDouble(), out[(15 + d) * 31 + 15].toDouble(), 1.0)
        }
        assertTrue(out[15 * 31 + 15] < 255)
    }
}
