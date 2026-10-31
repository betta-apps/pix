package io.betta.pix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeptFilmTest {
    private val noImage = { error("not loaded in a test") }

    @Test
    fun `a kept film's title, year and Plex link`() {
        val film = keptFilm("""{"title":"The Emperor's New Groove","year":2000,"link":"plex://x"}""", noImage)!!
        assertEquals("The Emperor's New Groove", film.title)
        assertEquals(2000, film.year)
        assertEquals("plex://x", film.link)
    }

    @Test
    fun `a details file cut off while saving is no film, not a crash`() {
        assertNull(keptFilm("", noImage))
        assertNull(keptFilm("""{"title":"Dress to""", noImage))
        assertNull(keptFilm("""{"year":1999}""", noImage))
    }
}
