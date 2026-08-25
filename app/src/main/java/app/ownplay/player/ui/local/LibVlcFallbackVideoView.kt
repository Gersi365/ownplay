package app.ownplay.player.ui.local

import android.content.Context
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.MediaController
import java.io.File
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * Final local-file playback fallback used only after Media3 and Android MediaPlayer fail.
 *
 * The caller supplies a private cache-copy path, so LibVLC never needs broad storage access.
 */
internal class LibVlcFallbackVideoView(
    context: Context,
) : FrameLayout(context), MediaController.MediaPlayerControl {
    var onPlaybackReady: (() -> Unit)? = null
    var onPlaybackError: ((String) -> Unit)? = null

    private val surfaceView = SurfaceView(context)
    private val controller = MediaController(context)
    private val libVlc = LibVLC(context.applicationContext)
    private val mediaPlayer = MediaPlayer(libVlc)
    private var openedPath: String? = null
    private var released = false

    private val gestures = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                controller.show()
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                val deltaMs = if (event.x < width / 2f) -SEEK_STEP_MS else SEEK_STEP_MS
                seekBy(deltaMs)
                controller.show()
                return true
            }
        },
    )

    init {
        addView(
            surfaceView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        controller.setAnchorView(this)

        surfaceView.isClickable = true
        surfaceView.setOnTouchListener { view, event ->
            val handled = gestures.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
            handled
        }
        surfaceView.setOnClickListener { controller.show() }

        mediaPlayer.vlcVout.setVideoView(surfaceView)
        mediaPlayer.vlcVout.attachViews()
        mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    onPlaybackReady?.invoke()
                    controller.show(CONTROLLER_INITIAL_SHOW_MS)
                }
                MediaPlayer.Event.EncounteredError -> {
                    onPlaybackError?.invoke("LibVLC could not play this local video.")
                }
                MediaPlayer.Event.EndReached -> controller.show()
            }
        }
    }

    fun openFile(path: String) {
        if (released || openedPath == path) return
        openedPath = path

        runCatching {
            val media = Media(libVlc, Uri.fromFile(File(path)))
            try {
                media.setHWDecoderEnabled(true, false)
                media.addOption(":file-caching=300")
                mediaPlayer.setMedia(media)
            } finally {
                media.release()
            }
            mediaPlayer.play()
            requestFocus()
        }.onFailure {
            onPlaybackError?.invoke("LibVLC could not open this local video.")
        }
    }

    fun releasePlayer() {
        if (released) return
        released = true
        runCatching { mediaPlayer.stop() }
        runCatching { mediaPlayer.setEventListener(null) }
        runCatching { mediaPlayer.vlcVout.detachViews() }
        runCatching { mediaPlayer.release() }
        runCatching { libVlc.release() }
        openedPath = null
        onPlaybackReady = null
        onPlaybackError = null
    }

    override fun start() {
        if (!released) runCatching { mediaPlayer.play() }
    }

    override fun pause() {
        if (!released) runCatching { mediaPlayer.pause() }
    }

    override fun getDuration(): Int =
        if (released) 0 else mediaPlayer.length.asControllerTime()

    override fun getCurrentPosition(): Int =
        if (released) 0 else mediaPlayer.time.asControllerTime()

    override fun seekTo(pos: Int) {
        if (!released) runCatching { mediaPlayer.setTime(pos.toLong()) }
    }

    override fun isPlaying(): Boolean = !released && runCatching { mediaPlayer.isPlaying }.getOrDefault(false)

    override fun getBufferPercentage(): Int = 100

    override fun canPause(): Boolean = true

    override fun canSeekBackward(): Boolean = !released && runCatching { mediaPlayer.isSeekable }.getOrDefault(false)

    override fun canSeekForward(): Boolean = canSeekBackward()

    override fun getAudioSessionId(): Int = 0

    private fun seekBy(deltaMs: Int) {
        val current = currentPosition
        val total = duration
        val upperBound = total.takeIf { it > 0 } ?: Int.MAX_VALUE
        val target = (current.toLong() + deltaMs)
            .coerceIn(0L, upperBound.toLong())
            .toInt()
        seekTo(target)
    }

    private fun Long.asControllerTime(): Int = when {
        this <= 0L -> 0
        this >= Int.MAX_VALUE.toLong() -> Int.MAX_VALUE
        else -> toInt()
    }

    private companion object {
        const val SEEK_STEP_MS = 10_000
        const val CONTROLLER_INITIAL_SHOW_MS = 1_500
    }
}
