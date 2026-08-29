from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file_path = Path(path)
    text = file_path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one replacement target, found {count}: {old[:120]!r}")
    file_path.write_text(text.replace(old, new, 1), encoding="utf-8")


bridge = "app/src/main/java/app/ownplay/player/playback/PlaybackInteractionBridge.kt"
replace_once(
    bridge,
    """    private var boundView = WeakReference<PlayerView>(null)\n    private var backOwner: Any? = null\n""",
    """    private var boundView = WeakReference<PlayerView>(null)\n    private var lifecycleSuspendedView = WeakReference<PlayerView>(null)\n    private var backOwner: Any? = null\n""",
)
replace_once(
    bridge,
    """    fun detachCurrent(output: PlaybackVideoOutput): Boolean {\n        val view = boundView.get() ?: return false\n        output.unbind(view)\n        observeUnboundView(view)\n        return true\n    }\n\n    fun observeBoundView(view: PlayerView) {\n        boundView = WeakReference(view)\n    }\n""",
    """    fun detachCurrent(output: PlaybackVideoOutput): Boolean {\n        lifecycleSuspendedView.clear()\n        val view = boundView.get() ?: return false\n        output.unbind(view)\n        observeUnboundView(view)\n        return true\n    }\n\n    /**\n     * Temporarily detaches the visible Live surface while its Activity is backgrounded. Unlike an\n     * intentional presentation handoff, this keeps only a WeakReference to the same PlayerView so\n     * it can be rebound if the Activity returns without recreation.\n     */\n    fun suspendCurrentForLifecycle(output: PlaybackVideoOutput): Boolean {\n        val view = boundView.get() ?: return false\n        lifecycleSuspendedView = WeakReference(view)\n        output.unbind(view)\n        observeUnboundView(view)\n        return true\n    }\n\n    fun resumeLifecycleSuspended(output: PlaybackVideoOutput): Boolean {\n        val view = lifecycleSuspendedView.get() ?: return false\n        lifecycleSuspendedView.clear()\n        if (!view.isAttachedToWindow) return false\n        output.bind(view)\n        return true\n    }\n\n    fun discardLifecycleSuspendedSurface() {\n        lifecycleSuspendedView.clear()\n    }\n\n    fun observeBoundView(view: PlayerView) {\n        lifecycleSuspendedView.clear()\n        boundView = WeakReference(view)\n    }\n""",
)

policy_path = Path("app/src/main/java/app/ownplay/player/playback/LiveActivityLifecyclePolicy.kt")
if policy_path.exists():
    raise RuntimeError(f"{policy_path}: already exists")
policy_path.write_text(
    '''package app.ownplay.player.playback\n\ninternal enum class LiveActivityBackgroundAction {\n    NONE,\n    SUSPEND_AND_RETAIN_SURFACE,\n}\n\n/**\n * Activity-level policy for Live only. PiP keeps ownership of playback, while configuration\n * recreation is allowed to tear down through Activity destruction instead of background suspend.\n */\ninternal object LiveActivityLifecyclePolicy {\n    fun backgroundAction(\n        state: PlaybackState,\n        inPictureInPicture: Boolean,\n        changingConfigurations: Boolean,\n    ): LiveActivityBackgroundAction {\n        if (inPictureInPicture || changingConfigurations) {\n            return LiveActivityBackgroundAction.NONE\n        }\n\n        val live = when (state) {\n            is PlaybackState.Loading -> state.request.mediaKind == PlaybackMediaKind.LIVE\n            is PlaybackState.Playing -> state.request.mediaKind == PlaybackMediaKind.LIVE\n            is PlaybackState.Paused -> state.request.mediaKind == PlaybackMediaKind.LIVE\n            PlaybackState.Idle,\n            is PlaybackState.Failed,\n            -> false\n        }\n        return if (live) {\n            LiveActivityBackgroundAction.SUSPEND_AND_RETAIN_SURFACE\n        } else {\n            LiveActivityBackgroundAction.NONE\n        }\n    }\n}\n''',
    encoding="utf-8",
)

activity = "app/src/main/java/app/ownplay/player/MainActivity.kt"
replace_once(
    activity,
    "import app.ownplay.player.playback.PlaybackInteractionBridge\n",
    """import app.ownplay.player.playback.LiveActivityBackgroundAction\nimport app.ownplay.player.playback.LiveActivityLifecyclePolicy\nimport app.ownplay.player.playback.PlaybackInteractionBridge\n""",
)
replace_once(
    activity,
    """    override fun onResume() {\n        super.onResume()\n        if (::runtime.isInitialized && tvRemoteGuardEnabled) {\n            runtime.playbackController.resumeAfterBackground()\n        }\n        hideStatusBar()\n""",
    """    override fun onResume() {\n        super.onResume()\n        if (::runtime.isInitialized) {\n            PlaybackInteractionBridge.resumeLifecycleSuspended(runtime.playbackVideoOutput)\n            runtime.playbackController.resumeAfterBackground()\n        }\n        hideStatusBar()\n""",
)
replace_once(
    activity,
    """    override fun onStop() {\n        if (\n            ::runtime.isInitialized &&\n            tvRemoteGuardEnabled &&\n            !isInPictureInPictureMode &&\n            !isChangingConfigurations\n        ) {\n            when (\n                TvPlaybackLifecyclePolicy.backgroundAction(\n                    runtime.playbackController.state.value,\n                )\n            ) {\n                TvBackgroundPlaybackAction.NONE -> Unit\n                TvBackgroundPlaybackAction.SUSPEND -> {\n                    runtime.playbackController.suspendForBackground()\n                }\n            }\n        }\n        super.onStop()\n    }\n""",
    """    override fun onStop() {\n        if (::runtime.isInitialized) {\n            val state = runtime.playbackController.state.value\n            when (\n                LiveActivityLifecyclePolicy.backgroundAction(\n                    state = state,\n                    inPictureInPicture = isInPictureInPictureMode,\n                    changingConfigurations = isChangingConfigurations,\n                )\n            ) {\n                LiveActivityBackgroundAction.SUSPEND_AND_RETAIN_SURFACE -> {\n                    PlaybackInteractionBridge.suspendCurrentForLifecycle(runtime.playbackVideoOutput)\n                    runtime.playbackController.suspendForBackground()\n                }\n                LiveActivityBackgroundAction.NONE -> {\n                    if (\n                        tvRemoteGuardEnabled &&\n                        !isInPictureInPictureMode &&\n                        !isChangingConfigurations\n                    ) {\n                        when (TvPlaybackLifecyclePolicy.backgroundAction(state)) {\n                            TvBackgroundPlaybackAction.NONE -> Unit\n                            TvBackgroundPlaybackAction.SUSPEND -> {\n                                runtime.playbackController.suspendForBackground()\n                            }\n                        }\n                    }\n                }\n            }\n        }\n        super.onStop()\n    }\n""",
)
replace_once(
    activity,
    """    override fun onDestroy() {\n        exitConfirmationDialog?.dismiss()\n        exitConfirmationDialog = null\n        activityScope.cancel()\n        offlineDownloadRuntime.close()\n        playbackWindowController.release()\n        runtime.close()\n        super.onDestroy()\n    }\n""",
    """    override fun onDestroy() {\n        exitConfirmationDialog?.dismiss()\n        exitConfirmationDialog = null\n        PlaybackInteractionBridge.discardLifecycleSuspendedSurface()\n        activityScope.cancel()\n        offlineDownloadRuntime.close()\n        playbackWindowController.release()\n        runtime.close()\n        super.onDestroy()\n    }\n""",
)

test_path = Path("app/src/test/java/app/ownplay/player/playback/LiveActivityLifecyclePolicyTest.kt")
if test_path.exists():
    raise RuntimeError(f"{test_path}: already exists")
test_path.write_text(
    '''package app.ownplay.player.playback\n\nimport org.junit.Assert.assertEquals\nimport org.junit.Test\n\nclass LiveActivityLifecyclePolicyTest {\n    @Test\n    fun activeLiveSuspendsOutsidePipAndConfigurationChange() {\n        val request = request(PlaybackMediaKind.LIVE)\n        listOf(\n            PlaybackState.Loading(request),\n            PlaybackState.Playing(request),\n            PlaybackState.Paused(request),\n        ).forEach { state ->\n            assertEquals(\n                LiveActivityBackgroundAction.SUSPEND_AND_RETAIN_SURFACE,\n                LiveActivityLifecyclePolicy.backgroundAction(\n                    state = state,\n                    inPictureInPicture = false,\n                    changingConfigurations = false,\n                ),\n            )\n        }\n    }\n\n    @Test\n    fun liveKeepsRunningWhenPipOwnsPlayback() {\n        assertEquals(\n            LiveActivityBackgroundAction.NONE,\n            LiveActivityLifecyclePolicy.backgroundAction(\n                state = PlaybackState.Playing(request(PlaybackMediaKind.LIVE)),\n                inPictureInPicture = true,\n                changingConfigurations = false,\n            ),\n        )\n    }\n\n    @Test\n    fun configurationChangeDoesNotCreateBackgroundSuspension() {\n        assertEquals(\n            LiveActivityBackgroundAction.NONE,\n            LiveActivityLifecyclePolicy.backgroundAction(\n                state = PlaybackState.Playing(request(PlaybackMediaKind.LIVE)),\n                inPictureInPicture = false,\n                changingConfigurations = true,\n            ),\n        )\n    }\n\n    @Test\n    fun nonLiveAndFailedStatesAreOutsideLiveLifecyclePolicy() {\n        listOf(PlaybackMediaKind.MOVIE, PlaybackMediaKind.SERIES_EPISODE).forEach { kind ->\n            assertEquals(\n                LiveActivityBackgroundAction.NONE,\n                LiveActivityLifecyclePolicy.backgroundAction(\n                    state = PlaybackState.Playing(request(kind)),\n                    inPictureInPicture = false,\n                    changingConfigurations = false,\n                ),\n            )\n        }\n        assertEquals(\n            LiveActivityBackgroundAction.NONE,\n            LiveActivityLifecyclePolicy.backgroundAction(\n                state = PlaybackState.Idle,\n                inPictureInPicture = false,\n                changingConfigurations = false,\n            ),\n        )\n    }\n\n    private fun request(kind: PlaybackMediaKind) = PlaybackRequest(\n        sourceId = "source",\n        channelId = "content",\n        mediaKind = kind,\n        providerStreamId = if (kind == PlaybackMediaKind.SERIES_EPISODE) 7 else null,\n    )\n}\n''',
    encoding="utf-8",
)

print("Applied Live Activity lifecycle hardening")
