package app.ownplay.player

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePresentationLifecycleRegressionTest {
    @Test
    fun mobileReadsLivePresentationFromProcessScopedSession() {
        val path = "src/mobile/java/app/ownplay/player/ui/MobileOwnPlayApp.kt"
        val source = sourceText(path)

        assertTrue(
            "$path must collect Live presentation from the process-scoped runtime session",
            source.contains("runtime.livePlaybackPresentationSession.state.collectAsState()"),
        )
        assertFalse(
            "$path must not own Preview selection in Activity-local Compose memory",
            source.contains("var activeSelection by remember"),
        )
        assertFalse(
            "$path must not own Fullscreen selection in Activity-local Compose memory",
            source.contains("var fullscreenSelection by remember"),
        )
        assertTrue(
            "$path must clear transient Live presentation on explicit teardown",
            source.contains("runtime.livePlaybackPresentationSession.clear()"),
        )
    }

    @Test
    fun rebuiltTvFoundationStartsPlaybackNeutralUntilNativeLiveStageIsWired() {
        val target = sourceText("src/tv/java/app/ownplay/player/ui/TargetOwnPlayApp.kt")
        val root = sourceText(
            "src/tv/java/app/ownplay/player/ui/rebuild/RebuiltOwnPlayTvApp.kt",
        )

        assertTrue(target.contains("RebuiltOwnPlayTvApp("))
        assertFalse(target.contains("TVOwnPlayApp("))
        assertFalse(root.contains("livePlaybackPresentationSession"))
        assertTrue(root.contains("onPlaybackFullscreenChanged(false)"))
        assertTrue(root.contains("onPlaybackSurfaceActiveChanged(false)"))
        assertTrue(root.contains("onLivePreviewActiveChanged(false)"))
    }

    @Test
    fun transientSessionIsNotDiskBacked() {
        val source = sourceText(
            "src/main/java/app/ownplay/player/playback/LivePlaybackPresentationSession.kt",
        )

        assertFalse(source.contains("DataStore"))
        assertFalse(source.contains("Room"))
        assertFalse(source.contains("SharedPreferences"))
        assertTrue(source.contains("MutableStateFlow"))
    }

    private fun sourceText(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
        )
        val source = candidates.firstOrNull(File::isFile)
            ?: error("Could not locate source file: $relativePath")
        return source.readText()
    }
}
