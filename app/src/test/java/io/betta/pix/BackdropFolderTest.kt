package io.betta.pix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BackdropFolderTest {
    // Films kept as yourself and as someone you stand in for are kept apart, so neither's show for the other.
    @Test
    fun aStandInKeepsFilmsApart() {
        assertEquals("backdrops", backdropFolder(standingIn = false))
        assertNotEquals(backdropFolder(standingIn = false), backdropFolder(standingIn = true))
    }
}
