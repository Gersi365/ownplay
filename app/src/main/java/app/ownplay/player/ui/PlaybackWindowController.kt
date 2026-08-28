package app.ownplay.player.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.view.View
import app.ownplay.player.personalization.AppOrientationMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class PlaybackOrientationIntent {
    PORTRAIT,
    LANDSCAPE,
    FOLLOW_SYSTEM,
    SENSOR,
}

internal object PlaybackWindowPolicy {
    fun isPipEligible(
        pipSupported: Boolean,
        isPlaying: Boolean,
        playbackSurfaceActive: Boolean,
    ): Boolean = pipSupported && isPlaying && playbackSurfaceActive

    fun orientationIntent(
        fullscreen: Boolean,
        appOrientation: AppOrientationMode,
        inPictureInPicture: Boolean,
        isTelevision: Boolean = false,
    ): PlaybackOrientationIntent = when {
        inPictureInPicture -> PlaybackOrientationIntent.FOLLOW_SYSTEM
        fullscreen && isTelevision -> PlaybackOrientationIntent.LANDSCAPE
        fullscreen -> PlaybackOrientationIntent.SENSOR
        appOrientation == AppOrientationMode.LANDSCAPE -> PlaybackOrientationIntent.LANDSCAPE
        else -> PlaybackOrientationIntent.PORTRAIT
    }
}

class PlaybackWindowController(
    private val activity: Activity,
) {
    val pipSupported: Boolean =
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private val isTelevision: Boolean =
        activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION

    private val _isInPictureInPictureMode =
        MutableStateFlow(activity.isInPictureInPictureMode)
    val isInPictureInPictureMode: StateFlow<Boolean> =
        _isInPictureInPictureMode.asStateFlow()

    private var isPlaying = false
    private var fullscreenRequested = false
    private var playbackSurfaceActive = false
    private var appOrientation = AppOrientationMode.PORTRAIT
    private var sourceRectHint: Rect? = null
    private var windowRoot: View? = null
    private var layoutListener: View.OnLayoutChangeListener? = null

    init {
        applyOrientationPolicy()
    }

    fun attachWindowRoot(view: View) {
        if (windowRoot === view) {
            refreshWindowState()
            return
        }
        detachWindowRoot()
        windowRoot = view
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateSourceRectHint()
            updatePictureInPictureParams()
        }
        layoutListener = listener
        view.addOnLayoutChangeListener(listener)
        refreshWindowState()
    }

    fun updatePlaybackState(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        updatePictureInPictureParams()
    }

    fun updateFullscreenState(fullscreen: Boolean) {
        if (fullscreenRequested == fullscreen) return
        fullscreenRequested = fullscreen
        applyOrientationPolicy()
    }

    fun updatePlaybackSurfaceState(active: Boolean) {
        if (playbackSurfaceActive == active) return
        playbackSurfaceActive = active
        updatePictureInPictureParams()
    }

    fun updateAppOrientation(mode: AppOrientationMode) {
        if (appOrientation == mode) return
        appOrientation = mode
        applyOrientationPolicy()
    }

    fun refreshWindowState() {
        updateSourceRectHint()
        applyOrientationPolicy()
        updatePictureInPictureParams()
    }

    fun requestPictureInPicture(): Boolean {
        if (!pipEligible() || activity.isInPictureInPictureMode || activity.isFinishing) {
            return false
        }
        return try {
            activity.enterPictureInPictureMode(buildPictureInPictureParams())
        } catch (_: IllegalStateException) {
            false
        }
    }

    fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            requestPictureInPicture()
        }
    }

    fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        _isInPictureInPictureMode.value = isInPictureInPictureMode
        applyOrientationPolicy()
        updatePictureInPictureParams()
    }

    fun release() {
        isPlaying = false
        fullscreenRequested = false
        playbackSurfaceActive = false
        detachWindowRoot()
        sourceRectHint = null
        updatePictureInPictureParams()
        applyOrientationPolicy()
    }

    private fun pipEligible(): Boolean = PlaybackWindowPolicy.isPipEligible(
        pipSupported = pipSupported,
        isPlaying = isPlaying,
        playbackSurfaceActive = playbackSurfaceActive,
    )

    private fun buildPictureInPictureParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        sourceRectHint?.takeIf { rect -> !rect.isEmpty() }?.let(builder::setSourceRectHint)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder
                .setAutoEnterEnabled(pipEligible())
                .setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun updatePictureInPictureParams() {
        if (!pipSupported || activity.isFinishing) return
        activity.setPictureInPictureParams(buildPictureInPictureParams())
    }

    private fun updateSourceRectHint() {
        val view = windowRoot ?: return
        if (!view.isAttachedToWindow) return
        val rect = Rect()
        if (view.getGlobalVisibleRect(rect) && !rect.isEmpty()) {
            sourceRectHint = Rect(rect)
        }
    }

    private fun detachWindowRoot() {
        val view = windowRoot
        val listener = layoutListener
        if (view != null && listener != null) {
            view.removeOnLayoutChangeListener(listener)
        }
        windowRoot = null
        layoutListener = null
    }

    private fun applyOrientationPolicy() {
        val target = when (
            PlaybackWindowPolicy.orientationIntent(
                fullscreen = fullscreenRequested,
                appOrientation = appOrientation,
                inPictureInPicture = _isInPictureInPictureMode.value,
                isTelevision = isTelevision,
            )
        ) {
            PlaybackOrientationIntent.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            PlaybackOrientationIntent.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            PlaybackOrientationIntent.FOLLOW_SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            PlaybackOrientationIntent.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        if (activity.requestedOrientation != target) {
            activity.requestedOrientation = target
        }
    }
}
