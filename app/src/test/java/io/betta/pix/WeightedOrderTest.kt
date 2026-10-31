package io.betta.pix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WeightedOrderTest {
    private fun movie(title: String, rating: Double?, watched: Boolean = false) = Plex.Movie(title, null, "/art", rating, watched)

    private fun firstCounts(movies: List<Plex.Movie>): Map<String, Int> {
        val random = Random(42)
        return (1..10_000).map { weightedOrder(movies, random).first().title }.groupingBy { it }.eachCount()
    }

    @Test
    fun `keeps every film exactly once`() {
        val movies = (1..50).map { movie("Film $it", it % 10.0) }
        assertEquals(movies.map { it.title }.sorted(), weightedOrder(movies, Random(1)).map { it.title }.sorted())
    }

    @Test
    fun `an 8 comes first about seven times as often as a 6`() {
        val counts = firstCounts(listOf(movie("Eight", 8.0), movie("Six", 6.0)))
        val ratio = counts.getValue("Eight").toDouble() / counts.getValue("Six")
        assertTrue("ratio was $ratio", ratio in 6.0..9.0)
    }

    @Test
    fun `an unrated film counts as a 6 and watching counts half again`() {
        val counts = firstCounts(listOf(movie("Unrated", null), movie("Watched", 6.0, watched = true)))
        val ratio = counts.getValue("Watched").toDouble() / counts.getValue("Unrated")
        assertTrue("ratio was $ratio", ratio in 1.3..1.7)
    }
}
