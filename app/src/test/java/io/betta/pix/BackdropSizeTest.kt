package io.betta.pix

import org.junit.Assert.assertEquals
import org.junit.Test

class BackdropSizeTest {
    @Test
    fun `a backdrop is decoded at the smallest fraction still as wide as the renderer develops it`() {
        assertEquals(1, backdropSample(3840, 2688))
        assertEquals(2, backdropSample(7680, 2688))
        assertEquals(2, backdropSample(5376, 2688))
        assertEquals(1, backdropSample(1920, 2688))
    }

    @Test
    fun `then shrunk to that width, keeping its shape, and never enlarged`() {
        assertEquals(2688 to 1512, shrunk(3840, 2160, 2688))
        assertEquals(2688 to 1120, shrunk(3840, 1600, 2688))
        assertEquals(1920 to 1080, shrunk(1920, 1080, 2688))
    }
}
