from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file_path = Path(path)
    text = file_path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one replacement target, found {count}: {old[:120]!r}")
    file_path.write_text(text.replace(old, new, 1), encoding="utf-8")


policy_path = Path("app/src/main/java/app/ownplay/player/ui/PictureInPictureSurfaceHandoffPolicy.kt")
if policy_path.exists():
    raise RuntimeError(f"Unexpected existing file: {policy_path}")
policy_path.write_text(
    '''package app.ownplay.player.ui

import app.ownplay.player.playback.PlaybackMediaKind

internal enum class PictureInPictureSurfaceBindingMode {
    MEDIA3_TRANSFER,
    DETACH_BEFORE_BIND,
}

/**
 * Keeps the existing Media3 target-transfer path for VOD/Series, while Live explicitly detaches
 * the current PlayerView before PiP binds another surface. This avoids reintroducing
 * PlayerView.switchTargetView for Live without changing PiP behavior for other media kinds.
 */
internal object PictureInPictureSurfaceHandoffPolicy {
    fun modeFor(mediaKind: PlaybackMediaKind?): PictureInPictureSurfaceBindingMode =
        if (mediaKind == PlaybackMediaKind.LIVE) {
            PictureInPictureSurfaceBindingMode.DETACH_BEFORE_BIND
        } else {
            PictureInPictureSurfaceBindingMode.MEDIA3_TRANSFER
        }

    fun handoff(
        mode: PictureInPictureSurfaceBindingMode,
        detachCurrentSurface: () -> Unit,
        bindDestinationSurface: () -> Unit,
    ) {
        if (mode == PictureInPictureSurfaceBindingMode.DETACH_BEFORE_BIND) {
            detachCurrentSurface()
        }
        bindDestinationSurface()
    }
}
''',
    encoding="utf-8",
)

pip_surface = "app/src/main/java/app/ownplay/player/ui/PictureInPicturePlaybackSurface.kt"
replace_once(
    pip_surface,
    "import app.ownplay.player.playback.PlaybackInteractionBridge\n",
    "import app.ownplay.player.playback.PlaybackInteractionBridge\nimport app.ownplay.player.playback.PlaybackMediaKind\n",
)
replace_once(
    pip_surface,
    """fun PictureInPicturePlaybackSurface(\n    videoOutput: PlaybackVideoOutput,\n    onProgress: ((positionMs: Long, durationMs: Long?) -> Unit)? = null,\n) {\n""",
    """fun PictureInPicturePlaybackSurface(\n    videoOutput: PlaybackVideoOutput,\n    mediaKind: PlaybackMediaKind? = null,\n    onProgress: ((positionMs: Long, durationMs: Long?) -> Unit)? = null,\n) {\n""",
)
replace_once(
    pip_surface,
    """    val returnTarget = remember { AtomicReference<WeakReference<PlayerView>?>(null) }\n    val progressCallback by rememberUpdatedState(onProgress)\n""",
    """    val returnTarget = remember { AtomicReference<WeakReference<PlayerView>?>(null) }\n    val progressCallback by rememberUpdatedState(onProgress)\n    val handoffMode = PictureInPictureSurfaceHandoffPolicy.modeFor(mediaKind)\n""",
)
replace_once(
    pip_surface,
    """                PlayerView(context).apply {\n                    useController = false\n                    setShutterBackgroundColor(AndroidColor.BLACK)\n                    videoOutput.bind(this)\n                    playerView = this\n                }\n""",
    """                PlayerView(context).apply {\n                    useController = false\n                    setShutterBackgroundColor(AndroidColor.BLACK)\n                    PictureInPictureSurfaceHandoffPolicy.handoff(\n                        mode = handoffMode,\n                        detachCurrentSurface = {\n                            PlaybackInteractionBridge.detachCurrent(videoOutput)\n                        },\n                        bindDestinationSurface = { videoOutput.bind(this) },\n                    )\n                    playerView = this\n                }\n""",
)
replace_once(
    pip_surface,
    """                    if (target != null) {\n                        videoOutput.bind(target)\n                    } else {\n                        videoOutput.unbind(view)\n                    }\n                    returnTarget.set(null)\n""",
    """                    if (target != null) {\n                        PictureInPictureSurfaceHandoffPolicy.handoff(\n                            mode = handoffMode,\n                            detachCurrentSurface = {\n                                PlaybackInteractionBridge.detachCurrent(videoOutput)\n                            },\n                            bindDestinationSurface = { videoOutput.bind(target) },\n                        )\n                    } else if (\n                        handoffMode == PictureInPictureSurfaceBindingMode.DETACH_BEFORE_BIND\n                    ) {\n                        PlaybackInteractionBridge.detachCurrent(videoOutput)\n                    } else {\n                        videoOutput.unbind(view)\n                    }\n                    returnTarget.set(null)\n""",
)

main_activity = "app/src/main/java/app/ownplay/player/MainActivity.kt"
replace_once(
    main_activity,
    """                                    PictureInPicturePlaybackSurface(\n                                        videoOutput = runtime.playbackVideoOutput,\n                                        onProgress = { positionMs, durationMs ->\n""",
    """                                    PictureInPicturePlaybackSurface(\n                                        videoOutput = runtime.playbackVideoOutput,\n                                        mediaKind = currentPlaybackMediaKind(),\n                                        onProgress = { positionMs, durationMs ->\n""",
)

test_path = Path("app/src/test/java/app/ownplay/player/ui/PictureInPictureSurfaceHandoffPolicyTest.kt")
if test_path.exists():
    raise RuntimeError(f"Unexpected existing file: {test_path}")
test_path.write_text(
    '''package app.ownplay.player.ui

import app.ownplay.player.playback.PlaybackMediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PictureInPictureSurfaceHandoffPolicyTest {
    @Test
    fun `live detaches before PiP destination bind`() {
        assertEquals(
            PictureInPictureSurfaceBindingMode.DETACH_BEFORE_BIND,
            PictureInPictureSurfaceHandoffPolicy.modeFor(PlaybackMediaKind.LIVE),
        )

        val events = mutableListOf<String>()
        PictureInPictureSurfaceHandoffPolicy.handoff(
            mode = PictureInPictureSurfaceBindingMode.DETACH_BEFORE_BIND,
            detachCurrentSurface = { events += "detach" },
            bindDestinationSurface = { events += "bind" },
        )

        assertEquals(listOf("detach", "bind"), events)
    }

    @Test
    fun `movie and series retain existing Media3 transfer path`() {
        listOf(
            PlaybackMediaKind.MOVIE,
            PlaybackMediaKind.SERIES_EPISODE,
            null,
        ).forEach { mediaKind ->
            assertEquals(
                PictureInPictureSurfaceBindingMode.MEDIA3_TRANSFER,
                PictureInPictureSurfaceHandoffPolicy.modeFor(mediaKind),
            )
        }

        val events = mutableListOf<String>()
        PictureInPictureSurfaceHandoffPolicy.handoff(
            mode = PictureInPictureSurfaceBindingMode.MEDIA3_TRANSFER,
            detachCurrentSurface = { events += "detach" },
            bindDestinationSurface = { events += "bind" },
        )

        assertEquals(listOf("bind"), events)
    }
}
''',
    encoding="utf-8",
)

print("Applied Live PiP surface handoff hardening patch")
