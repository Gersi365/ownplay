package app.ownplay.player.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class PlaybackOrientationIntent {
    FOLLOW_SYSTEM,
    SENSOR_LANDSCAPE,
}

internal object PlaybackWindowPolicy {
    private const val LARGE_SCREEN_SMALLEST_WIDTH_DP = 600

    fun isPipEligible(
        pipSupported: Boolean,
        isPlaying: Boolean,
    ): Boolean = pipSupported && isPlaying

    fun orientationIntent(
        fullscreen: Boolean,
        inPictureInPicture: Boolean,
        smallestScreenWidthDp: Int,
    ): PlaybackOrientationIntent = if (
        fullscreen &&
        !inPictureInPicture &&
        smallestScreenWidthDp in 1 until LARGE_SCREEN_SMALLEST_WIDTH_DP
    ) {
        PlaybackOrientationIntent.SENSOR_LANDSCAPE
    } else {
        PlaybackOrientationIntent.FOLLOW_SYSTEM
    }
}

class PlaybackWindowController(
    private val activity: Activity,
) {
    val pipSupported: Boolean =
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private val _isInPictureInPictureMode =
        MutableStateFlow(activity.isInPictureInPictureMode)
    val isInPictureInPictureMode: StateFlow<Boolean> =
        _isInPictureInPictureMode.asStateFlow()

    private var isPlaying = false
    private var fullscreen = false
    private var sourceRectHint: Rect? = null
    private var windowRoot: View? = null
    private var layoutListener: View.OnLayoutChangeListener? = null

    fun attachWindowRoot(view: View) {
        if (windowRoot === view) {
            refreshWindowState()
            return
        }
        detachWindowRoot()
        windowRoot = view
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            refreshWindowState()
        }
        layoutListener = listener
        view.addOnLayoutChangeListener(listener)
        refreshWindowState()
    }

    fun updatePlaybackState(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        updatePictureInPictureParams()
    }

    fun refreshWindowState() {
        updateSourceRectHint()
        fullscreen = detectFullscreen()
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
        fullscreen = false
        detachWindowRoot()
        sourceRectHint = null
        updatePictureInPictureParams()
        applyOrientationPolicy()
    }

    private fun pipEligible(): Boolean = PlaybackWindowPolicy.isPipEligible(
        pipSupported = pipSupported,
        isPlaying = isPlaying,
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

    private fun detectFullscreen(): Boolean {
        if (_isInPictureInPictureMode.value) return false
        val root = windowRoot ?: return false
        val insets = ViewCompat.getRootWindowInsets(root) ?: return false
        return !insets.isVisible(WindowInsetsCompat.Type.systemBars())
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
                fullscreen = fullscreen,
                inPictureInPicture = _isInPictureInPictureMode.value,
                smallestScreenWidthDp = activity.resources.configuration.smallestScreenWidthDp,
            )
        ) {
            PlaybackOrientationIntent.FOLLOW_SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            PlaybackOrientationIntent.SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (activity.requestedOrientation != target) {
            activity.requestedOrientation = target
        }
    }
}
