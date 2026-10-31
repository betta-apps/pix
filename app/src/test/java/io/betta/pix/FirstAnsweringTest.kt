package io.betta.pix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstAnsweringTest {
    @Test
    fun `an address that hangs doesn't hold up one that answers`() {
        val started = System.nanoTime()
        val found = firstAnswering(listOf("docker-bridge", "lan")) { address ->
            if (address == "docker-bridge") Thread.sleep(3_000)
            address == "lan"
        }
        assertEquals("lan", found)
        assertTrue("took ${(System.nanoTime() - started) / 1_000_000} ms", System.nanoTime() - started < 1_000_000_000)
    }

    @Test
    fun `none answering is null`() {
        assertNull(firstAnswering(listOf("a", "b")) { false })
        assertNull(firstAnswering(emptyList()) { true })
    }
}
