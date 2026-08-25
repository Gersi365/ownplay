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
 * This compatibility path deliberately disables hardware decoding to avoid vendor MediaCodec
 * failures for media that already failed the platform playback stack.
 */
internal class LibVlcFallbackVideoView(
    context: Context,
) : FrameLayout(context), MediaController.MediaPlayerControl {
    var onPlaybackReady: (() -> Unit)? = null
    var onPlaybackError: ((String) -> Unit)? = null

    private val surfaceView = SurfaceView(context)
    private val controller = MediaController(context)
    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var initializationError: String? = null
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

        runCatching {
            val vlc = LibVLC(context.applicationContext)
            libVlc = vlc
            val player = MediaPlayer(vlc)
            mediaPlayer = player

            player.vlcVout.setVideoView(surfaceView)
            player.vlcVout.attachViews()
            player.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
            player.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        onPlaybackReady?.invoke()
                        controller.show(CONTROLLER_INITIAL_SHOW_MS)
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        onPlaybackError?.invoke(
                            "LibVLC software-decoding fallback could not play this local video.",
                        )
                    }
                    MediaPlayer.Event.EndReached -> controller.show()
                }
            }
        }.onFailure {
            initializationError = "LibVLC compatibility fallback could not initialize safely."
            releaseNativeResources()
        }
    }

    fun openFile(path: String) {
        if (released || openedPath == path) return

        val initializationFailure = initializationError
        val vlc = libVlc
        val player = mediaPlayer
        if (initializationFailure != null || vlc == null || player == null) {
            onPlaybackError?.invoke(
                initializationFailure ?: "LibVLC compatibility fallback is unavailable on this device.",
            )
            return
        }

        openedPath = path
        runCatching {
            val media = Media(vlc, Uri.fromFile(File(path)))
            try {
                media.setHWDecoderEnabled(false, false)
                media.addOption(":file-caching=300")
                player.setMedia(media)
            } finally {
                media.release()
            }
            player.play()
            requestFocus()
        }.onFailure {
            onPlaybackError?.invoke("LibVLC software-decoding fallback could not open this local video.")
        }
    }

    fun releasePlayer() {
        if (released) return
        released = true
        releaseNativeResources()
        openedPath = null
        onPlaybackReady = null
        onPlaybackError = null
    }

    override fun start() {
        val player = mediaPlayer ?: return
        if (!released) runCatching { player.play() }
    }

    override fun pause() {
        val player = mediaPlayer ?: return
        if (!released) runCatching { player.pause() }
    }

    override fun getDuration(): Int =
        if (released) 0 else mediaPlayer?.length?.asControllerTime() ?: 0

    override fun getCurrentPosition(): Int =
        if (released) 0 else mediaPlayer?.time?.asControllerTime() ?: 0

    override fun seekTo(pos: Int) {
        val player = mediaPlayer ?: return
        if (!released) runCatching { player.setTime(pos.toLong()) }
    }

    override fun isPlaying(): Boolean {
        val player = mediaPlayer ?: return false
        return !released && runCatching { player.isPlaying }.getOrDefault(false)
    }

    override fun getBufferPercentage(): Int = 100

    override fun canPause(): Boolean = mediaPlayer != null && !released

    override fun canSeekBackward(): Boolean {
        val player = mediaPlayer ?: return false
        return !released && runCatching { player.isSeekable }.getOrDefault(false)
    }

    override fun canSeekForward(): Boolean = canSeekBackward()

    override fun getAudioSessionId(): Int = 0

    private fun releaseNativeResources() {
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            runCatching { player.stop() }
            runCatching { player.setEventListener(null) }
            runCatching { player.vlcVout.detachViews() }
            runCatching { player.release() }
        }

        val vlc = libVlc
        libVlc = null
        if (vlc != null) runCatching { vlc.release() }
    }

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
