package io.betta.pix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YourServerTest {
    // As yourself: a server you own. Standing in for someone: only yours, by its identifier, and none while it's unknown.
    @Test
    fun standingInTakesOnlyYourServer() {
        assertTrue(isYourServer(standingIn = false, yours = null, id = "a", owned = true))
        assertFalse(isYourServer(standingIn = false, yours = "a", id = "a", owned = false))
        assertTrue(isYourServer(standingIn = true, yours = "a", id = "a", owned = false))
        assertFalse(isYourServer(standingIn = true, yours = "a", id = "b", owned = true))
        assertFalse(isYourServer(standingIn = true, yours = null, id = "b", owned = true))
    }
}
