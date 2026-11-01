package io.betta.pix

import org.junit.Assert.assertEquals
import org.junit.Test

class BakeBandsTest {
    @Test
    fun `the bands cover the whole film, each row once, in order`() {
        for (bands in listOf(1, 4, 6, 7)) {
            val rows = (0 until bands).map { bandRows(it, bands, 1512) }
            assertEquals(0, rows.first().first)
            assertEquals(1512, rows.last().second)
            rows.zipWithNext().forEach { (a, b) -> assertEquals(a.second, b.first) }
        }
    }

    @Test
    fun `six bands of 1512 rows are 252 each`() {
        assertEquals(listOf(252), (0 until 6).map { bandRows(it, 6, 1512) }.map { it.second - it.first }.distinct())
    }
}
