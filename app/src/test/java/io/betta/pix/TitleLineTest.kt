package io.betta.pix

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleLineTest {
    // Ten to a character, spaces too, on one line unless a test breaks it.
    private val measure = { s: String -> s.length * 10f }

    @Test
    fun `each word is its own stretch, with a space's gap between`() {
        val lines = wordLines("The Third Man", { 0 }, { 99 }, { it * 10f }, measure)
        assertEquals(listOf(0 to listOf(0f to 30f, 40f to 90f, 100f to 130f)), lines)
    }

    @Test
    fun `runs of spaces are one wider gap, and punctuation belongs to its word`() {
        val lines = wordLines("Wall-E,  Again", { 0 }, { 99 }, { it * 10f }, measure)
        assertEquals(listOf(0 to listOf(0f to 70f, 90f to 140f)), lines)
    }

    @Test
    fun `a title that wraps keeps each word on its own line, the first line first`() {
        // "Eternal Sunshine" on the first line, "of the Spotless Mind" on the second, starting again from the left.
        val text = "Eternal Sunshine of the Spotless Mind"
        val wrap = text.indexOf("of")
        val lines = wordLines(text, { if (it < wrap) 0 else 1 }, { if (it == 0) wrap else text.length }, { (if (it < wrap) it else it - wrap) * 10f }, measure)
        assertEquals(listOf(0, 1), lines.map { it.first })
        assertEquals(listOf(0f to 70f, 80f to 160f), lines[0].second)
        assertEquals(listOf(0f to 20f, 30f to 60f, 70f to 150f, 160f to 200f), lines[1].second)
    }

    @Test
    fun `a word broken across two lines is a piece on each`() {
        // "Supercalifragilistic" broken after "Supercali": nine letters on the first line, the rest on the second.
        val text = "Supercalifragilistic"
        val lines = wordLines(text, { if (it < 9) 0 else 1 }, { if (it == 0) 9 else text.length }, { (if (it < 9) it else it - 9) * 10f }, measure)
        assertEquals(listOf(0 to listOf(0f to 90f), 1 to listOf(0f to 110f)), lines)
    }
}
