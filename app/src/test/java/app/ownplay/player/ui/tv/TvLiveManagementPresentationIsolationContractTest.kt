package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveManagementPresentationIsolationContractTest {
    private val settingsSource by lazy {
        normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvSettingsScreen.kt"),
        )
    }
    private val sharedSettingsSource by lazy {
        normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/SettingsScreen.kt"),
        )
    }
    private val tvShellSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/TVOwnPlayApp.kt"),
        )
    }
    private val managementSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/live/TvLiveManagementScreen.kt"),
        )
    }

    @Test
    fun `tv live management presentation is injected only from the tv source set`() {
        assertTrue(
            "Shared Settings must expose a flavor-safe TV Live Management content injection.",
            "tvLiveManagementContent:" in sharedSettingsSource,
        )
        assertTrue(
            "Shared Settings must pass the injection into its TV Settings root.",
            "liveManagementContent = tvLiveManagementContent" in sharedSettingsSource,
        )
        assertTrue(
            "Shared TV Settings must invoke only the injected content.",
            "liveManagementContent(" in settingsSource,
        )
        assertFalse(
            "Shared TV Settings must not import a TV-only implementation.",
            "import app.ownplay.player.ui.live.TvLiveManagementScreen" in settingsSource,
        )
        assertTrue(
            "The TV shell must inject the dedicated source-set implementation.",
            "TvLiveManagementScreen(" in tvShellSource,
        )
        assertTrue(
            "The TV-only implementation must be imported only by the TV shell.",
            "import app.ownplay.player.ui.live.TvLiveManagementScreen" in tvShellSource,
        )
    }

    @Test
    fun `tv management excludes touch derived presentation primitives`() {
        assertFalse("TV path must not embed generic LiveBrowseScreen.", "LiveBrowseScreen(" in managementSource)
        assertFalse("TV path must not embed CategoryReorderSheet.", "CategoryReorderSheet(" in managementSource)
        assertFalse("TV path must not use outlined text fields.", "OutlinedTextField" in managementSource)
        assertFalse("TV path must not use filter chips.", "FilterChip" in managementSource)
        assertFalse("TV path must not use dropdown menus.", "DropdownMenu" in managementSource)
        assertFalse("TV path must not use modal bottom sheets.", "ModalBottomSheet" in managementSource)
        assertFalse("TV path must not use pointer drag handling.", "pointerInput" in managementSource)
        assertFalse("TV focus must not scale layout geometry.", ".scale(" in managementSource)
        assertFalse("TV focus must not animate row size.", "animateContentSize" in managementSource)
        assertFalse(
            "TV source must not import the internal Compose weight member.",
            "import androidx.compose.foundation.layout.weight" in managementSource,
        )
    }

    @Test
    fun `tv management keeps stable remote first geometry and visible focus`() {
        assertTrue("Channel rows must use fixed geometry.", ".height(64.dp)" in managementSource)
        assertTrue("Action rows must use fixed geometry.", ".height(54.dp)" in managementSource)
        assertTrue("Compact action rows must use fixed geometry.", ".height(48.dp)" in managementSource)
        assertTrue(
            "Focused actions must change container color rather than geometry.",
            "focused -> MaterialTheme.colorScheme.primaryContainer" in managementSource,
        )
        assertTrue(
            "TV text entry must use the dedicated stable text-input surface.",
            "BasicTextField(" in managementSource && "TvManagementTextInput(" in managementSource,
        )
    }

    @Test
    fun `tv management uses dedicated pages for complex remote actions`() {
        assertTrue("Category ordering must be a dedicated page.", "TvLiveManagementPage.CATEGORY_ORDER" in managementSource)
        assertTrue("Custom groups must be a dedicated page.", "TvLiveManagementPage.CUSTOM_GROUPS" in managementSource)
        assertTrue(
            "Channel customization must be a dedicated page.",
            "TvLiveManagementPage.CHANNEL_CUSTOMIZATION" in managementSource,
        )
        assertTrue(
            "Nested pages must intercept Back before returning to Settings.",
            "BackHandler(enabled = page != TvLiveManagementPage.MAIN)" in managementSource,
        )
    }

    @Test
    fun `tv management preserves established personalization runtime paths`() {
        assertTrue("Category visibility must remain runtime-owned.", "runtime.hideCategory(" in managementSource)
        assertTrue("Category ordering must remain runtime-owned.", "runtime.setCategoryOrder(" in managementSource)
        assertTrue("Channel ordering must remain runtime-owned.", "runtime.moveChannelRelative(" in managementSource)
        assertTrue("Bulk channel actions must remain runtime-owned.", "runtime.executeChannelBulkAction(" in managementSource)
        assertTrue("Custom groups must remain runtime-owned.", "runtime.createCustomGroup(" in managementSource)
        assertTrue("Local names must remain runtime-owned.", "runtime.setLocalDisplayName(" in managementSource)
        assertTrue("Logo overrides must remain runtime-owned.", "runtime.setLogoOverride(" in managementSource)
    }
}
