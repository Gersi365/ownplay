package app.ownplay.player.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTvFocusPolicyTest {
    @Test
    fun `top level settings destinations keep initial focus on the rail`() {
        assertTrue(settingsRailShouldOwnFocus(SettingsDestination.INTERFACE))
        assertTrue(settingsRailShouldOwnFocus(SettingsDestination.CONTENT))
        assertTrue(settingsRailShouldOwnFocus(SettingsDestination.DOWNLOADS))
        assertTrue(settingsRailShouldOwnFocus(SettingsDestination.ABOUT))
    }

    @Test
    fun `nested settings destinations keep focus inside their content`() {
        assertFalse(settingsRailShouldOwnFocus(SettingsDestination.LIVE_MANAGEMENT))
        assertFalse(settingsRailShouldOwnFocus(SettingsDestination.PLAYLISTS))
    }

    @Test
    fun `settings rail highlight is color driven by selected or focused state`() {
        assertTrue(settingsRailItemHighlighted(selected = true, focused = false))
        assertTrue(settingsRailItemHighlighted(selected = false, focused = true))
        assertTrue(settingsRailItemHighlighted(selected = true, focused = true))
        assertFalse(settingsRailItemHighlighted(selected = false, focused = false))
    }
}
