package app.ownplay.player

import android.content.res.Configuration
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.ownplay.player.download.OfflineDownloadFeatureRuntime
import app.ownplay.player.personalization.AppDeviceProfile
import app.ownplay.player.personalization.AppDeviceProfileSelection
import app.ownplay.player.personalization.AppDeviceProfileStore
import app.ownplay.player.personalization.AppOrientationMode
import app.ownplay.player.playback.PlaybackInteractionBridge
import app.ownplay.player.playback.PlaybackMediaKind
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.ui.DeviceProfileSetupScreen
import app.ownplay.player.ui.DownloadPlaybackBridge
import app.ownplay.player.ui.OrientationSetupLoadingSurface
import app.ownplay.player.ui.OwnPlayRoot
import app.ownplay.player.ui.PictureInPicturePlaybackSurface
import app.ownplay.player.ui.PlaybackOriginBadge
import app.ownplay.player.ui.PlaybackWindowController
import app.ownplay.player.ui.SourceSubmissionCoordinator
import app.ownplay.player.ui.library.LibraryPlaybackScreen
import app.ownplay.player.ui.library.LibraryPlaybackSession
import app.ownplay.player.ui.theme.OwnPlayTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val DOUBLE_TAP_SEEK_MILLIS = 10_000L

class MainActivity : ComponentActivity() {
    private lateinit var runtime: OwnPlayAppRuntime
    private lateinit var offlineDownloadRuntime: OfflineDownloadFeatureRuntime
    private lateinit var playbackWindowController: PlaybackWindowController
    private lateinit var appDeviceProfileStore: AppDeviceProfileStore
    private lateinit var playbackGestureDetector: GestureDetector
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var playbackFullscreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = OwnPlayAppRuntime(applicationContext)
        offlineDownloadRuntime = OfflineDownloadFeatureRuntime(applicationContext)
        appDeviceProfileStore = AppDeviceProfileStore(applicationContext)
        playbackWindowController = PlaybackWindowController(this)
        val preferredSetupProfile =
            if (
                resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
                Configuration.UI_MODE_TYPE_TELEVISION
            ) {
                AppDeviceProfile.ANDROID_TV
            } else {
                AppDeviceProfile.SMARTPHONE
            }
        playbackGestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (!playbackFullscreen) return false
                    val mediaKind = currentPlaybackMediaKind()
                    if (
                        mediaKind != PlaybackMediaKind.MOVIE &&
                        mediaKind != PlaybackMediaKind.SERIES_EPISODE
                    ) {
                        return false
                    }
                    val deltaMillis = if (e.x < window.decorView.width / 2f) {
                        -DOUBLE_TAP_SEEK_MILLIS
                    } else {
                        DOUBLE_TAP_SEEK_MILLIS
                    }
                    return PlaybackInteractionBridge.seekBy(deltaMillis)
                }
            },
        )
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (PlaybackInteractionBridge.handleBack()) return
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            },
        )
        playbackWindowController.refreshWindowState()
        enableEdgeToEdge()
        hideStatusBar()
        setContent {
            val isInPictureInPictureMode by
                playbackWindowController.isInPictureInPictureMode.collectAsState()
            val deviceProfileSelection by appDeviceProfileStore.observeSelection().collectAsState(
                initial = AppDeviceProfileSelection.Loading,
            )
            val configuredProfile =
                (deviceProfileSelection as? AppDeviceProfileSelection.Configured)
                    ?.settings
                    ?.profile
            val playbackOrigin by runtime.playbackController.resolvedOrigin.collectAsState()
            var downloadPlaybackSession by remember {
                mutableStateOf<LibraryPlaybackSession?>(null)
            }
            val downloadPlaybackOwner = remember { Any() }

            SideEffect {
                PlaybackInteractionBridge.setDpadMode(configuredProfile?.usesDpad == true)
            }

            DisposableEffect(downloadPlaybackOwner) {
                DownloadPlaybackBridge.register(downloadPlaybackOwner) { download ->
                    activityScope.launch {
                        val request = offlineDownloadRuntime.playbackRequest(download.downloadId)
                        if (request == null) {
                            offlineDownloadRuntime.reconcileCompletedFiles()
                            Toast.makeText(
                                applicationContext,
                                "The offline file is unavailable. Download it again to restore offline playback.",
                                Toast.LENGTH_LONG,
                            ).show()
                            return@launch
                        }
                        val progress = offlineDownloadRuntime.playbackProgress(download.downloadId)
                        runtime.playbackController.start(request)
                        downloadPlaybackSession = LibraryPlaybackSession(
                            download = download,
                            initialPositionMs = progress
                                ?.takeIf { !it.completed }
                                ?.positionMs
                                ?.coerceAtLeast(0L)
                                ?: 0L,
                        )
                    }
                }
                onDispose {
                    DownloadPlaybackBridge.clear(downloadPlaybackOwner)
                }
            }

            OwnPlayTheme(deviceProfile = configuredProfile) {
                when (deviceProfileSelection) {
                    AppDeviceProfileSelection.Loading -> {
                        OrientationSetupLoadingSurface()
                    }
                    AppDeviceProfileSelection.Unconfigured -> {
                        DeviceProfileSetupScreen(
                            preferredProfile = preferredSetupProfile,
                            onConfigured = { profile, smartphoneOrientation ->
                                activityScope.launch {
                                    if (
                                        appDeviceProfileStore.configure(
                                            profile = profile,
                                            smartphoneOrientation = smartphoneOrientation,
                                        )
                                    ) {
                                        playbackWindowController.updateTelevisionMode(profile.usesDpad)
                                        playbackWindowController.updateAppOrientation(
                                            configuredOrientation(
                                                profile = profile,
                                                smartphoneOrientation = smartphoneOrientation,
                                            ),
                                        )
                                    }
                                }
                            },
                        )
                    }
                    is AppDeviceProfileSelection.Configured -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            OwnPlayRoot(
                                runtime = runtime,
                                onPlaybackFullscreenChanged = { isFullscreen ->
                                    playbackFullscreen = isFullscreen
                                    playbackWindowController.updateFullscreenState(isFullscreen)
                                    if (!isFullscreen) hideStatusBar()
                                },
                                onPlaybackSurfaceActiveChanged =
                                    playbackWindowController::updatePlaybackSurfaceState,
                            )

                            when {
                                isInPictureInPictureMode -> {
                                    PictureInPicturePlaybackSurface(
                                        videoOutput = runtime.playbackVideoOutput,
                                        onProgress = { positionMs, durationMs ->
                                            val request = when (
                                                val state = runtime.playbackController.state.value
                                            ) {
                                                is PlaybackState.Playing -> state.request
                                                is PlaybackState.Paused -> state.request
                                                else -> null
                                            }
                                            if (request != null) {
                                                activityScope.launch {
                                                    offlineDownloadRuntime.savePlaybackProgress(
                                                        request = request,
                                                        positionMs = positionMs,
                                                        durationMs = durationMs,
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }
                                downloadPlaybackSession != null -> {
                                    val session = downloadPlaybackSession ?: return@OwnPlayTheme
                                    LibraryPlaybackScreen(
                                        runtime = runtime,
                                        session = session,
                                        onExit = {
                                            runtime.playbackController.stop()
                                            downloadPlaybackSession = null
                                        },
                                        onProgress = { positionMs, durationMs ->
                                            activityScope.launch {
                                                offlineDownloadRuntime.savePlaybackProgress(
                                                    downloadId = session.download.downloadId,
                                                    positionMs = positionMs,
                                                    durationMs = durationMs,
                                                )
                                            }
                                        },
                                        onFullscreenStateChanged = { isFullscreen ->
                                            playbackFullscreen = isFullscreen
                                            playbackWindowController.updateFullscreenState(isFullscreen)
                                            playbackWindowController.updatePlaybackSurfaceState(isFullscreen)
                                            if (!isFullscreen) hideStatusBar()
                                        },
                                        backContentDescription = "Back to Downloads",
                                        contextLabel = "Downloads · offline copy",
                                    )
                                    playbackOrigin?.let { origin ->
                                        PlaybackOriginBadge(
                                            origin = origin,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(top = 10.dp, end = 12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        playbackWindowController.attachWindowRoot(findViewById(android.R.id.content))
        activityScope.launch {
            appDeviceProfileStore.observeSelection().collectLatest { selection ->
                if (selection is AppDeviceProfileSelection.Configured) {
                    playbackWindowController.updateTelevisionMode(selection.settings.profile.usesDpad)
                    playbackWindowController.updateAppOrientation(
                        selection.settings.effectiveOrientation,
                    )
                }
            }
        }
        activityScope.launch {
            runtime.playbackController.state.collectLatest { state ->
                playbackWindowController.updatePlaybackState(state is PlaybackState.Playing)
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        playbackGestureDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    override fun onResume() {
        super.onResume()
        hideStatusBar()
        if (::offlineDownloadRuntime.isInitialized) {
            activityScope.launch {
                offlineDownloadRuntime.reconcileCompletedFiles()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        playbackWindowController.onUserLeaveHint()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playbackWindowController.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        playbackWindowController.refreshWindowState()
        hideStatusBar()
    }

    override fun onDestroy() {
        activityScope.cancel()
        SourceSubmissionCoordinator.release(runtime)
        offlineDownloadRuntime.close()
        playbackWindowController.release()
        runtime.close()
        super.onDestroy()
    }

    private fun currentPlaybackMediaKind(): PlaybackMediaKind? =
        when (val state = runtime.playbackController.state.value) {
            PlaybackState.Idle -> null
            is PlaybackState.Loading -> state.request.mediaKind
            is PlaybackState.Playing -> state.request.mediaKind
            is PlaybackState.Paused -> state.request.mediaKind
            is PlaybackState.Failed -> state.request.mediaKind
        }

    private fun hideStatusBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }
}

private fun configuredOrientation(
    profile: AppDeviceProfile,
    smartphoneOrientation: AppOrientationMode?,
): AppOrientationMode = if (profile == AppDeviceProfile.SMARTPHONE) {
    smartphoneOrientation ?: AppOrientationMode.PORTRAIT
} else {
    AppOrientationMode.LANDSCAPE
}
