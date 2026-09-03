package app.ownplay.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpgGuideFocusPolicyTest {
    @Test
    fun `last focused program wins when still valid`() {
        assertEquals(3, epgGuideFocusIndex(programCount = 5, currentIndex = 2, lastFocusedIndex = 3))
    }

    @Test
    fun `current program is initial TV focus when no row was focused before`() {
        assertEquals(2, epgGuideFocusIndex(programCount = 5, currentIndex = 2, lastFocusedIndex = null))
    }

    @Test
    fun `first program is fallback when there is no current program`() {
        assertEquals(0, epgGuideFocusIndex(programCount = 5, currentIndex = null, lastFocusedIndex = null))
    }

    @Test
    fun `invalid remembered row falls back to current program`() {
        assertEquals(1, epgGuideFocusIndex(programCount = 2, currentIndex = 1, lastFocusedIndex = 7))
    }

    @Test
    fun `empty guide has no program focus target`() {
        assertNull(epgGuideFocusIndex(programCount = 0, currentIndex = null, lastFocusedIndex = null))
    }
}
