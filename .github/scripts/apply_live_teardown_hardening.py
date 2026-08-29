from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file_path = Path(path)
    text = file_path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one replacement target, found {count}: {old[:120]!r}")
    file_path.write_text(text.replace(old, new, 1), encoding="utf-8")


presentation = "app/src/main/java/app/ownplay/player/playback/LivePlaybackPresentation.kt"
replace_once(
    presentation,
    """object LivePlaybackSurfaceHandoff {\n    fun restartAcrossPresentation(\n        detachCurrentSurface: () -> Boolean,\n        stopPlayback: () -> Unit,\n        switchPresentation: () -> Unit,\n        startPlayback: () -> Unit,\n    ): Boolean {\n        val detached = detachCurrentSurface()\n        stopPlayback()\n        switchPresentation()\n        startPlayback()\n        return detached\n    }\n}\n\nenum class LivePlaybackPresentationSurface {\n""",
    """object LivePlaybackSurfaceHandoff {\n    fun restartAcrossPresentation(\n        detachCurrentSurface: () -> Boolean,\n        stopPlayback: () -> Unit,\n        switchPresentation: () -> Unit,\n        startPlayback: () -> Unit,\n    ): Boolean {\n        val detached = detachCurrentSurface()\n        stopPlayback()\n        switchPresentation()\n        startPlayback()\n        return detached\n    }\n}\n\n/**\n * Stops Live while leaving its current presentation instead of handing off to another surface.\n * The departing PlayerView is detached before the stream is stopped and before Compose clears the\n * presentation state. This prevents a stale Live surface from being reused by a later bind.\n * VOD, Series, and PiP do not use this helper.\n */\nobject LivePlaybackSurfaceTeardown {\n    fun stopAfterDetaching(\n        detachCurrentSurface: () -> Boolean,\n        stopPlayback: () -> Unit,\n        clearPresentation: () -> Unit,\n    ): Boolean {\n        val detached = detachCurrentSurface()\n        stopPlayback()\n        clearPresentation()\n        return detached\n    }\n}\n\nenum class LivePlaybackPresentationSurface {\n""",
)

app = "app/src/main/java/app/ownplay/player/ui/OwnPlayApp.kt"
replace_once(
    app,
    "import app.ownplay.player.playback.LivePlaybackSelection\n",
    "import app.ownplay.player.playback.LivePlaybackSelection\nimport app.ownplay.player.playback.LivePlaybackSurfaceTeardown\n",
)
replace_once(
    app,
    """    fun returnLiveToPreview(selection: LivePlaybackSelection) {\n        liveTransitionGate.requestHandoff(\n            target = LivePlaybackTransitionTarget.preview(selection),\n            detachCurrentSurface = {\n                PlaybackInteractionBridge.detachCurrent(runtime.playbackVideoOutput)\n            },\n            stopPlayback = runtime.playbackController::stop,\n            switchPresentation = {\n                activeSourceId = selection.request.sourceId\n                section = OwnPlaySection.LIVE\n                activeSelection = selection\n                fullscreenSelection = null\n                fullscreenEntryReason = null\n            },\n            startPlayback = { runtime.playbackController.start(selection.request) },\n        )\n    }\n\n    LaunchedEffect(summaries) {\n""",
    """    fun returnLiveToPreview(selection: LivePlaybackSelection) {\n        liveTransitionGate.requestHandoff(\n            target = LivePlaybackTransitionTarget.preview(selection),\n            detachCurrentSurface = {\n                PlaybackInteractionBridge.detachCurrent(runtime.playbackVideoOutput)\n            },\n            stopPlayback = runtime.playbackController::stop,\n            switchPresentation = {\n                activeSourceId = selection.request.sourceId\n                section = OwnPlaySection.LIVE\n                activeSelection = selection\n                fullscreenSelection = null\n                fullscreenEntryReason = null\n            },\n            startPlayback = { runtime.playbackController.start(selection.request) },\n        )\n    }\n\n    fun stopLivePlaybackSurface(clearPresentation: () -> Unit) {\n        LivePlaybackSurfaceTeardown.stopAfterDetaching(\n            detachCurrentSurface = {\n                PlaybackInteractionBridge.detachCurrent(runtime.playbackVideoOutput)\n            },\n            stopPlayback = runtime.playbackController::stop,\n            clearPresentation = clearPresentation,\n        )\n    }\n\n    LaunchedEffect(summaries) {\n""",
)
replace_once(
    app,
    """        if (selectionSourceId != null && selectionSourceId !in ids) {\n            activeSelection = null\n            fullscreenSelection = null\n            fullscreenEntryReason = null\n            runtime.playbackController.stop()\n        }\n""",
    """        if (selectionSourceId != null && selectionSourceId !in ids) {\n            stopLivePlaybackSurface {\n                activeSelection = null\n                fullscreenSelection = null\n                fullscreenEntryReason = null\n            }\n        }\n""",
)
replace_once(
    app,
    """    fun openContentSection(target: OwnPlaySection) {\n        if (target != OwnPlaySection.LIVE && activeSelection != null) {\n            activeSelection = null\n            fullscreenSelection = null\n            fullscreenEntryReason = null\n            runtime.playbackController.stop()\n        }\n""",
    """    fun openContentSection(target: OwnPlaySection) {\n        if (target != OwnPlaySection.LIVE && activeSelection != null) {\n            stopLivePlaybackSurface {\n                activeSelection = null\n                fullscreenSelection = null\n                fullscreenEntryReason = null\n            }\n        }\n""",
)
replace_once(
    app,
    """                                onPreviewClosed = {\n                                    activeSelection = null\n                                    runtime.playbackController.stop()\n                                },\n""",
    """                                onPreviewClosed = {\n                                    stopLivePlaybackSurface {\n                                        activeSelection = null\n                                    }\n                                },\n""",
)
replace_once(
    app,
    "                                onOpenSettings = { section = OwnPlaySection.SETTINGS },\n",
    "                                onOpenSettings = { openContentSection(OwnPlaySection.SETTINGS) },\n",
)
replace_once(
    app,
    """                        onOpenSourceInLive = { sourceId ->\n                            if (sourceId != activeSourceId) {\n                                activeSelection = null\n                                fullscreenSelection = null\n                                fullscreenEntryReason = null\n                                runtime.playbackController.stop()\n                            }\n                            activeSourceId = sourceId\n                            section = OwnPlaySection.LIVE\n                        },\n                        onStopPlayback = {\n                            activeSelection = null\n                            fullscreenSelection = null\n                            fullscreenEntryReason = null\n                            runtime.playbackController.stop()\n                        },\n""",
    """                        onOpenSourceInLive = { sourceId ->\n                            if (sourceId != activeSourceId) {\n                                if (activeSelection != null || fullscreenSelection != null) {\n                                    stopLivePlaybackSurface {\n                                        activeSelection = null\n                                        fullscreenSelection = null\n                                        fullscreenEntryReason = null\n                                    }\n                                }\n                            }\n                            activeSourceId = sourceId\n                            section = OwnPlaySection.LIVE\n                        },\n                        onStopPlayback = {\n                            if (activeSelection != null || fullscreenSelection != null) {\n                                stopLivePlaybackSurface {\n                                    activeSelection = null\n                                    fullscreenSelection = null\n                                    fullscreenEntryReason = null\n                                }\n                            } else {\n                                runtime.playbackController.stop()\n                            }\n                        },\n""",
)

test = "app/src/test/java/app/ownplay/player/playback/LivePlaybackPresentationPolicyTest.kt"
replace_once(
    test,
    """    @Test\n    fun `transition gate ignores repeated request before compose acknowledgement`() {\n""",
    """    @Test\n    fun `live teardown detaches before stop and presentation clear`() {\n        val events = mutableListOf<String>()\n\n        val detached = LivePlaybackSurfaceTeardown.stopAfterDetaching(\n            detachCurrentSurface = {\n                events += \"detach\"\n                true\n            },\n            stopPlayback = { events += \"stop\" },\n            clearPresentation = { events += \"clear\" },\n        )\n\n        assertTrue(detached)\n        assertEquals(listOf(\"detach\", \"stop\", \"clear\"), events)\n    }\n\n    @Test\n    fun `live teardown still stops and clears when no surface is bound`() {\n        val events = mutableListOf<String>()\n\n        val detached = LivePlaybackSurfaceTeardown.stopAfterDetaching(\n            detachCurrentSurface = {\n                events += \"detach\"\n                false\n            },\n            stopPlayback = { events += \"stop\" },\n            clearPresentation = { events += \"clear\" },\n        )\n\n        assertFalse(detached)\n        assertEquals(listOf(\"detach\", \"stop\", \"clear\"), events)\n    }\n\n    @Test\n    fun `transition gate ignores repeated request before compose acknowledgement`() {\n""",
)

print("Applied Live teardown hardening patch")
