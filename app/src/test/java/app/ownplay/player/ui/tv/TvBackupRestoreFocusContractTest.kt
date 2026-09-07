package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertTrue
import org.junit.Test

class TvBackupRestoreFocusContractTest {
    @Test
    fun `backup restore enters on export primary action on television`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/BackupRestoreSettingsContent.kt"),
        )

        assertTrue(
            "TV entry must explicitly request the Backup & Restore primary action.",
            "if (isTelevision) {" in source &&
                "primaryActionFocusRequester.requestFocus()" in source,
        )
        assertTrue(
            "Primary focus must be attached to the Export action.",
            "actionLabel = \"Export\"" in source &&
                "actionModifier = Modifier.focusRequester(primaryActionFocusRequester)" in source,
        )
        assertTrue(
            "TV primary focus must run after the subpage header focus pass.",
            "withFrameNanos { }" in source,
        )
    }
}
