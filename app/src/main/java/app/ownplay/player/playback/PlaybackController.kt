package app.ownplay.player.playback

import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface PlaybackEngine {
    interface Listener {
        fun onReady()
        fun onPlaying()
        fun onPaused()
        fun onEnded()
        fun onFailure(failure: PlaybackFailure)
    }

    fun setListener(listener: Listener?)
    fun prepare(locator: ResolvedPlaybackLocator)
    fun play()
    fun pause()
    fun stop()
    fun release()
}

class PlaybackController(
    private val resolveLocator: suspend (PlaybackRequest) -> PlaybackResolutionResult,
    private val engine: PlaybackEngine,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val loadingTimeoutMillis: Long = DEFAULT_LOADING_TIMEOUT_MILLIS,
) : AutoCloseable {
    private val controllerJob = SupervisorJob()
    private val scope = CoroutineScope(controllerJob + mainDispatcher)
    private val mutableState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)

    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    private var resolutionJob: Job? = null
    private var timeoutJob: Job? = null
    private var generation: Long = 0L
    private var desiredPlayWhenReady: Boolean = true
    @Volatile
    private var released: Boolean = false

    init {
        require(loadingTimeoutMillis > 0L) { "Loading timeout must be positive" }
        engine.setListener(
            object : PlaybackEngine.Listener {
                override fun onReady() {
                    if (released) return
                    timeoutJob?.cancel()
                    val prepared = PlaybackReducer.reduce(mutableState.value, PlaybackEvent.Prepared)
                    mutableState.value = if (desiredPlayWhenReady) {
                        prepared
                    } else {
                        PlaybackReducer.reduce(prepared, PlaybackEvent.Pause)
                    }
                }

                override fun onPlaying() {
                    if (released) return
                    mutableState.value = PlaybackReducer.reduce(mutableState.value, PlaybackEvent.Play)
                }

                override fun onPaused() {
                    if (released) return
                    mutableState.value = PlaybackReducer.reduce(mutableState.value, PlaybackEvent.Pause)
                }

                override fun onEnded() {
                    if (released) return
                    timeoutJob?.cancel()
                    failCurrent(PlaybackFailure(PlaybackFailureCategory.STREAM_UNAVAILABLE))
                }

                override fun onFailure(failure: PlaybackFailure) {
                    if (released) return
                    timeoutJob?.cancel()
                    failCurrent(failure)
                }
            },
        )
    }

    fun start(request: PlaybackRequest) {
        check(!released) { "PlaybackController is released" }
        scope.launch {
            startOnControllerDispatcher(request)
        }
    }

    fun play() {
        check(!released) { "PlaybackController is released" }
        scope.launch {
            desiredPlayWhenReady = true
            engine.play()
            mutableState.value = PlaybackReducer.reduce(mutableState.value, PlaybackEvent.Play)
        }
    }

    fun pause() {
        check(!released) { "PlaybackController is released" }
        scope.launch {
            desiredPlayWhenReady = false
            engine.pause()
            mutableState.value = PlaybackReducer.reduce(mutableState.value, PlaybackEvent.Pause)
        }
    }

    fun retry() {
        check(!released) { "PlaybackController is released" }
        scope.launch {
            val failed = mutableState.value as? PlaybackState.Failed ?: return@launch
            if (!failed.failure.retryable) return@launch
            startOnControllerDispatcher(failed.request)
        }
    }

    fun stop() {
        check(!released) { "PlaybackController is released" }
        scope.launch {
            stopOnControllerDispatcher()
        }
    }

    override fun close() {
        if (released) return
        released = true
        generation += 1
        resolutionJob?.cancel()
        timeoutJob?.cancel()
        engine.setListener(null)
        engine.stop()
        engine.release()
        mutableState.value = PlaybackState.Idle
        scope.cancel()
    }

    private fun startOnControllerDispatcher(request: PlaybackRequest) {
        generation += 1
        val requestGeneration = generation

        resolutionJob?.cancel()
        timeoutJob?.cancel()
        engine.stop()

        desiredPlayWhenReady = true
        mutableState.value = PlaybackReducer.reduce(
            mutableState.value,
            PlaybackEvent.Start(request),
        )

        timeoutJob = scope.launch {
            delay(loadingTimeoutMillis)
            if (
                !released &&
                requestGeneration == generation &&
                mutableState.value is PlaybackState.Loading
            ) {
                resolutionJob?.cancel()
                engine.stop()
                failCurrent(PlaybackFailure(PlaybackFailureCategory.TIMEOUT))
            }
        }

        resolutionJob = scope.launch {
            val result = try {
                withContext(ioDispatcher) {
                    resolveLocator(request)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PlaybackResolutionResult.Failure(
                    PlaybackResolutionFailureReason.PERSISTENCE_FAILURE,
                )
            }

            ensureActive()
            if (released || requestGeneration != generation) return@launch

            when (result) {
                is PlaybackResolutionResult.Success -> {
                    engine.prepare(result.locator)
                    engine.play()
                }
                is PlaybackResolutionResult.Failure -> {
                    timeoutJob?.cancel()
                    failCurrent(result.reason.toPlaybackFailure())
                }
            }
        }
    }

    private fun stopOnControllerDispatcher() {
        generation += 1
        resolutionJob?.cancel()
        timeoutJob?.cancel()
        engine.stop()
        mutableState.value = PlaybackReducer.reduce(mutableState.value, PlaybackEvent.Stop)
    }

    private fun failCurrent(failure: PlaybackFailure) {
        mutableState.value = PlaybackReducer.reduce(
            mutableState.value,
            PlaybackEvent.Fail(failure),
        )
    }

    companion object {
        const val DEFAULT_LOADING_TIMEOUT_MILLIS: Long = 30_000L
    }
}

internal fun PlaybackResolutionFailureReason.toPlaybackFailure(): PlaybackFailure {
    val category = when (this) {
        PlaybackResolutionFailureReason.CLEARTEXT_NOT_ALLOWED,
        PlaybackResolutionFailureReason.UNSUPPORTED_SOURCE_KIND,
        PlaybackResolutionFailureReason.DESCRIPTOR_INVALID,
        PlaybackResolutionFailureReason.SOURCE_LOCATOR_INVALID,
        -> PlaybackFailureCategory.UNSUPPORTED_MEDIA

        PlaybackResolutionFailureReason.SOURCE_NOT_FOUND,
        PlaybackResolutionFailureReason.SOURCE_DISABLED,
        PlaybackResolutionFailureReason.CHANNEL_NOT_FOUND,
        PlaybackResolutionFailureReason.SOURCE_CHANNEL_MISMATCH,
        PlaybackResolutionFailureReason.CHANNEL_REMOVED,
        PlaybackResolutionFailureReason.DESCRIPTOR_REFERENCE_INVALID,
        PlaybackResolutionFailureReason.DESCRIPTOR_NOT_FOUND,
        PlaybackResolutionFailureReason.SOURCE_LOCATOR_REFERENCE_INVALID,
        PlaybackResolutionFailureReason.SOURCE_LOCATOR_NOT_FOUND,
        -> PlaybackFailureCategory.STREAM_UNAVAILABLE

        PlaybackResolutionFailureReason.SECURE_STORE_FAILURE,
        PlaybackResolutionFailureReason.CREDENTIAL_REFERENCE_MISSING,
        PlaybackResolutionFailureReason.CREDENTIAL_REFERENCE_INVALID,
        PlaybackResolutionFailureReason.CREDENTIALS_NOT_FOUND,
        PlaybackResolutionFailureReason.CREDENTIALS_INVALID,
        PlaybackResolutionFailureReason.CREDENTIAL_STORE_FAILURE,
        PlaybackResolutionFailureReason.PERSISTENCE_FAILURE,
        -> PlaybackFailureCategory.UNKNOWN
    }
    return PlaybackFailure(category)
}
