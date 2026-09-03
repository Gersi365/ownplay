package app.ownplay.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnPlayShellBackPolicyTest {
    @Test
    fun liveIsTheOnlyShellExitRoot() {
        assertNull(ownPlayShellBackTarget(OwnPlaySection.LIVE))
    }

    @Test
    fun movieAndSeriesSectionsReturnToLibrary() {
        assertEquals(
            OwnPlaySection.LIBRARY,
            ownPlayShellBackTarget(OwnPlaySection.MOVIES),
        )
        assertEquals(
            OwnPlaySection.LIBRARY,
            ownPlayShellBackTarget(OwnPlaySection.SERIES),
        )
    }

    @Test
    fun libraryAndSettingsReturnToLive() {
        assertEquals(
            OwnPlaySection.LIVE,
            ownPlayShellBackTarget(OwnPlaySection.LIBRARY),
        )
        assertEquals(
            OwnPlaySection.LIVE,
            ownPlayShellBackTarget(OwnPlaySection.SETTINGS),
        )
    }
}
