package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvBackupRestoreFocusContractTest {
    @Test
    fun `backup restore enters on the dedicated export row on television`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/BackupRestoreSettingsContent.kt"),
        )

        assertTrue(
            "TV entry must explicitly request the Backup & Restore primary action.",
            "if (isTelevision) {" in source &&
                "primaryActionFocusRequester.requestFocus()" in source,
        )
        assertTrue(
            "Primary focus must be attached to the full TV Export row.",
            "TvBackupRestoreActionRow(" in source &&
                "actionLabel = \"Export\"" in source &&
                "modifier = Modifier.focusRequester(primaryActionFocusRequester)" in source,
        )
        assertTrue(
            "TV primary focus must run after the subpage header focus pass.",
            "withFrameNanos { }" in source,
        )
    }

    @Test
    fun `tv backup actions use stable ownplay row geometry instead of icon buttons`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/BackupRestoreSettingsContent.kt"),
        )
        val tvRow = source
            .substringAfter("private fun TvBackupRestoreActionRow(")
            .substringBefore("private suspend fun writeBackup(")

        assertTrue("TV backup rows must keep fixed height.", ".height(68.dp)" in tvRow)
        assertTrue(
            "Focused TV backup rows must use the OwnPlay focus container.",
            "MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.84f)" in tvRow,
        )
        assertFalse("TV backup row must not use the shared IconButton control.", "IconButton(" in tvRow)
        assertFalse("TV backup focus must not scale layout geometry.", ".scale(" in tvRow)
    }
}
