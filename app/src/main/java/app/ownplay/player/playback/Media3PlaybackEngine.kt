package app.ownplay.player.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer

class Media3PlaybackEngine(
    context: Context,
) : PlaybackEngine {
    private val player = ExoPlayer.Builder(context.applicationContext).build()
    private val playerHandler = Handler(player.applicationLooper)
    private var listener: PlaybackEngine.Listener? = null

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> listener?.onReady()
                        Player.STATE_ENDED -> listener?.onEnded()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        listener?.onPlaying()
                    } else if (
                        player.playbackState == Player.STATE_READY &&
                        !player.playWhenReady
                    ) {
                        listener?.onPaused()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    listener?.onFailure(Media3PlaybackFailureMapper.map(error))
                }
            },
        )
    }

    override fun setListener(listener: PlaybackEngine.Listener?) {
        runOnPlayerThread {
            this.listener = listener
        }
    }

    override fun prepare(locator: ResolvedPlaybackLocator) {
        runOnPlayerThread {
            player.setMediaItem(MediaItem.fromUri(locator.value))
            player.prepare()
        }
    }

    override fun play() {
        runOnPlayerThread {
            player.play()
        }
    }

    override fun pause() {
        runOnPlayerThread {
            player.pause()
        }
    }

    override fun stop() {
        runOnPlayerThread {
            player.stop()
            player.clearMediaItems()
        }
    }

    override fun release() {
        runOnPlayerThread {
            player.stop()
            player.clearMediaItems()
            listener = null
            player.release()
        }
    }

    private fun runOnPlayerThread(action: () -> Unit) {
        if (Looper.myLooper() == player.applicationLooper) {
            action()
        } else {
            playerHandler.post(action)
        }
    }
}

@OptIn(UnstableApi::class)
internal object Media3PlaybackFailureMapper {
    fun map(error: PlaybackException): PlaybackFailure =
        map(
            errorCode = error.errorCode,
            httpStatusCode = error.cause.findHttpStatusCode(),
        )

    fun map(
        errorCode: Int,
        httpStatusCode: Int? = null,
    ): PlaybackFailure {
        val category = when (errorCode) {
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            -> PlaybackFailureCategory.TIMEOUT

            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                PlaybackFailureCategory.NETWORK_UNAVAILABLE

            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> when (httpStatusCode) {
                401, 403 -> PlaybackFailureCategory.AUTHENTICATION_FAILURE
                else -> PlaybackFailureCategory.STREAM_UNAVAILABLE
            }

            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            -> PlaybackFailureCategory.STREAM_UNAVAILABLE

            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            -> PlaybackFailureCategory.UNSUPPORTED_MEDIA

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            -> PlaybackFailureCategory.STREAM_UNAVAILABLE

            else -> PlaybackFailureCategory.UNKNOWN
        }
        return PlaybackFailure(category)
    }

    private fun Throwable?.findHttpStatusCode(): Int? {
        var current = this
        while (current != null) {
            if (current is HttpDataSource.InvalidResponseCodeException) {
                return current.responseCode
            }
            current = current.cause
        }
        return null
    }
}

object Media3PlaybackControllerFactory {
    fun create(
        context: Context,
        resolver: LivePlaybackResolver,
    ): PlaybackController = PlaybackController(
        resolveLocator = resolver::resolve,
        engine = Media3PlaybackEngine(context),
    )
}
