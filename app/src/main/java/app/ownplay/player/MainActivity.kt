package app.ownplay.player

import android.content.res.Configuration
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.ownplay.player.download.OfflineDownloadFeatureRuntime
import app.ownplay.player.personalization.AppOrientationSelection
import app.ownplay.player.personalization.AppOrientationStore
import app.ownplay.player.playback.PlaybackInteractionBridge
import app.ownplay.player.playback.PlaybackMediaKind
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.ui.OrientationSetupLoadingSurface
import app.ownplay.player.ui.OrientationSetupScreen
import app.ownplay.player.ui.OwnPlayRoot
import app.ownplay.player.ui.PictureInPicturePlaybackSurface
import app.ownplay.player.ui.PlaybackWindowController
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
    private lateinit var appOrientationStore: AppOrientationStore
    private lateinit var playbackGestureDetector: GestureDetector
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var playbackFullscreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = OwnPlayAppRuntime(applicationContext)
        offlineDownloadRuntime = OfflineDownloadFeatureRuntime(applicationContext)
        appOrientationStore = AppOrientationStore(applicationContext)
        playbackWindowController = PlaybackWindowController(this)
        playbackGestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (!playbackFullscreen) return false
                    val mediaKind = when (val state = runtime.playbackController.state.value) {
                        PlaybackState.Idle -> null
                        is PlaybackState.Loading -> state.request.mediaKind
                        is PlaybackState.Playing -> state.request.mediaKind
                        is PlaybackState.Paused -> state.request.mediaKind
                        is PlaybackState.Failed -> state.request.mediaKind
                    }
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
            val orientationSelection by appOrientationStore.observeSelection().collectAsState(
                initial = AppOrientationSelection.Loading,
            )
            OwnPlayTheme {
                when {
                    isInPictureInPictureMode -> {
                        PictureInPicturePlaybackSurface(runtime.playbackVideoOutput)
                    }
                    orientationSelection == AppOrientationSelection.Loading -> {
                        OrientationSetupLoadingSurface()
                    }
                    orientationSelection == AppOrientationSelection.Unconfigured -> {
                        OrientationSetupScreen(
                            onOrientationSelected = { mode ->
                                activityScope.launch {
                                    if (appOrientationStore.set(mode)) {
                                        playbackWindowController.updateAppOrientation(mode)
                                    }
                                }
                            },
                        )
                    }
                    else -> {
                        OwnPlayRoot(
                            runtime = runtime,
                            onPlaybackFullscreenChanged = { isFullscreen ->
                                playbackFullscreen = isFullscreen
                                playbackWindowController.updateFullscreenState(isFullscreen)
                                if (!isFullscreen) hideStatusBar()
                            },
                            onPlaybackSurfaceActiveChanged = playbackWindowController::updatePlaybackSurfaceState,
                        )
                    }
                }
            }
        }
        playbackWindowController.attachWindowRoot(findViewById(android.R.id.content))
        activityScope.launch {
            appOrientationStore.observeSelection().collectLatest { selection ->
                if (selection is AppOrientationSelection.Configured) {
                    playbackWindowController.updateAppOrientation(selection.mode)
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
        offlineDownloadRuntime.close()
        playbackWindowController.release()
        runtime.close()
        super.onDestroy()
    }

    private fun hideStatusBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }
}