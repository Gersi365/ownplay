package app.ownplay.player.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface PlaybackVideoOutput {
    fun bind(view: PlayerView)
    fun unbind(view: PlayerView)
}

@OptIn(UnstableApi::class)
class Media3PlaybackEngine(
    context: Context,
) : PlaybackEngine, PlaybackVideoOutput, PlaybackTrackController {
    private val applicationContext = context.applicationContext
    private val renderersFactory = DefaultRenderersFactory(applicationContext)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    private val player = ExoPlayer.Builder(applicationContext, renderersFactory)
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .build()
    private val playerHandler = Handler(player.applicationLooper)
    private val mutableTrackState = MutableStateFlow(PlaybackTrackState())
    private var listener: PlaybackEngine.Listener? = null
    private var boundVideoView: PlayerView? = null
    private var nextTrackId: Long = 1L
    private val trackIdsByKey = mutableMapOf<Media3TrackKey, String>()
    private val trackHandlesById = mutableMapOf<String, Media3TrackHandle>()

    override val state: StateFlow<PlaybackTrackState> = mutableTrackState.asStateFlow()

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

                override fun onTracksChanged(tracks: Tracks) {
                    publishTracks(tracks)
                    ensureSupportedAudioSelected(tracks)
                }

                override fun onPlayerError(error: PlaybackException) {
                    listener?.onFailure(Media3PlaybackFailureMapper.map(error))
                }
            },
        )
        player.addAnalyticsListener(
            object : AnalyticsListener {
                override fun onAudioDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    decoderName: String,
                    initializedTimestampMs: Long,
                    initializationDurationMs: Long,
                ) {
                    updateDiagnostics { diagnostics ->
                        diagnostics.copy(audioDecoder = safeDiagnosticValue(decoderName))
                    }
                }

                override fun onVideoDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    decoderName: String,
                    initializedTimestampMs: Long,
                    initializationDurationMs: Long,
                ) {
                    updateDiagnostics { diagnostics ->
                        diagnostics.copy(videoDecoder = safeDiagnosticValue(decoderName))
                    }
                }

                override fun onDownstreamFormatChanged(
                    eventTime: AnalyticsListener.EventTime,
                    mediaLoadData: MediaLoadData,
                ) {
                    if (mediaLoadData.trackType != C.TRACK_TYPE_VIDEO) return
                    val format = mediaLoadData.trackFormat ?: return
                    updateDiagnostics { diagnostics ->
                        diagnostics.withCurrentVideoFormat(format)
                    }
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
            resetTrackSelectionForNewMedia()
            clearTrackSession()
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
            clearTrackSession()
        }
    }

    override fun selectAudio(selection: PlaybackAudioSelection) {
        runOnPlayerThread {
            when (selection) {
                PlaybackAudioSelection.Default -> {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .build()
                    mutableTrackState.value = PlaybackTrackSelectionPolicy.selectAudio(
                        mutableTrackState.value,
                        selection,
                    )
                }

                is PlaybackAudioSelection.Specific -> {
                    val handle = trackHandlesById[selection.trackId]
                        ?.takeIf { it.kind == PlaybackTrackKind.AUDIO && it.supported }
                        ?: return@runOnPlayerThread
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setOverrideForType(
                            TrackSelectionOverride(
                                handle.trackGroup,
                                handle.trackIndex,
                            ),
                        )
                        .build()
                    mutableTrackState.value = PlaybackTrackSelectionPolicy.selectAudio(
                        mutableTrackState.value,
                        selection,
                    )
                }
            }
        }
    }

    override fun selectSubtitle(selection: PlaybackSubtitleSelection) {
        runOnPlayerThread {
            when (selection) {
                PlaybackSubtitleSelection.Default -> {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .build()
                    mutableTrackState.value = PlaybackTrackSelectionPolicy.selectSubtitle(
                        mutableTrackState.value,
                        selection,
                    )
                }

                PlaybackSubtitleSelection.Off -> {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    mutableTrackState.value = PlaybackTrackSelectionPolicy.selectSubtitle(
                        mutableTrackState.value,
                        selection,
                    )
                }

                is PlaybackSubtitleSelection.Specific -> {
                    val handle = trackHandlesById[selection.trackId]
                        ?.takeIf { it.kind == PlaybackTrackKind.SUBTITLE && it.supported }
                        ?: return@runOnPlayerThread
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(
                            TrackSelectionOverride(
                                handle.trackGroup,
                                handle.trackIndex,
                            ),
                        )
                        .build()
                    mutableTrackState.value = PlaybackTrackSelectionPolicy.selectSubtitle(
                        mutableTrackState.value,
                        selection,
                    )
                }
            }
        }
    }

    override fun bind(view: PlayerView) {
        runOnPlayerThread {
            view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            if (boundVideoView === view) {
                PlaybackInteractionBridge.observeBoundView(view)
                return@runOnPlayerThread
            }
            boundVideoView?.player = null
            view.useController = false
            view.player = player
            boundVideoView = view
            PlaybackInteractionBridge.observeBoundView(view)
        }
    }

    override fun unbind(view: PlayerView) {
        runOnPlayerThread {
            if (boundVideoView === view) {
                PlaybackInteractionBridge.observeUnboundView(view)
                view.player = null
                boundVideoView = null
            }
        }
    }

    override fun release() {
        runOnPlayerThread {
            boundVideoView?.let(PlaybackInteractionBridge::observeUnboundView)
            boundVideoView?.player = null
            boundVideoView = null
            player.stop()
            player.clearMediaItems()
            clearTrackSession()
            listener = null
            player.release()
        }
    }

    private fun publishTracks(tracks: Tracks) {
        val audioTracks = mutableListOf<PlaybackTrackOption>()
        val subtitleTracks = mutableListOf<PlaybackTrackOption>()
        val liveHandlesById = mutableMapOf<String, Media3TrackHandle>()
        val liveKeys = mutableSetOf<Media3TrackKey>()
        var audioOrdinal = 0
        var subtitleOrdinal = 0

        for (group in tracks.groups) {
            val kind = when (group.type) {
                C.TRACK_TYPE_AUDIO -> PlaybackTrackKind.AUDIO
                C.TRACK_TYPE_TEXT -> PlaybackTrackKind.SUBTITLE
                else -> continue
            }

            for (trackIndex in 0 until group.length) {
                val ordinal = when (kind) {
                    PlaybackTrackKind.AUDIO -> ++audioOrdinal
                    PlaybackTrackKind.SUBTITLE -> ++subtitleOrdinal
                }
                val key = Media3TrackKey(group.mediaTrackGroup, trackIndex)
                liveKeys += key
                val id = trackIdsByKey.getOrPut(key) {
                    val prefix = when (kind) {
                        PlaybackTrackKind.AUDIO -> "audio"
                        PlaybackTrackKind.SUBTITLE -> "subtitle"
                    }
                    "$prefix-${nextTrackId++}"
                }
                val format = group.getTrackFormat(trackIndex)
                val supported = group.isTrackSupported(trackIndex)
                val option = PlaybackTrackOption(
                    id = id,
                    kind = kind,
                    label = PlaybackTrackLabelFormatter.format(
                        kind = kind,
                        rawLabel = format.label,
                        rawLanguage = format.language,
                        ordinal = ordinal,
                    ),
                    language = PlaybackTrackLabelFormatter.language(format.language),
                    selectedByPlayer = group.isTrackSelected(trackIndex),
                    supported = supported,
                )
                liveHandlesById[id] = Media3TrackHandle(
                    kind = kind,
                    trackGroup = group.mediaTrackGroup,
                    trackIndex = trackIndex,
                    supported = supported,
                )
                when (kind) {
                    PlaybackTrackKind.AUDIO -> audioTracks += option
                    PlaybackTrackKind.SUBTITLE -> subtitleTracks += option
                }
            }
        }

        trackIdsByKey.keys.retainAll(liveKeys)
        trackHandlesById.clear()
        trackHandlesById.putAll(liveHandlesById)
        val stateWithDiagnostics = mutableTrackState.value.copy(
            diagnostics = diagnosticsFromTracks(
                tracks = tracks,
                previous = mutableTrackState.value.diagnostics,
            ),
        )
        mutableTrackState.value = PlaybackTrackSelectionPolicy.withTracks(
            state = stateWithDiagnostics,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
        )
    }

    private fun diagnosticsFromTracks(
        tracks: Tracks,
        previous: PlaybackDiagnostics,
    ): PlaybackDiagnostics {
        val audio = selectedFormat(tracks, C.TRACK_TYPE_AUDIO)
        val currentVideo = player.videoFormat
        val current = if (currentVideo != null) {
            previous.withCurrentVideoFormat(currentVideo)
        } else {
            previous
        }
        return current.copy(
            audioMimeType = safeDiagnosticValue(audio?.sampleMimeType),
            audioCodecs = safeDiagnosticValue(audio?.codecs),
            audioSampleRate = audio?.sampleRate?.positiveOrNull(),
            audioChannelCount = audio?.channelCount?.positiveOrNull(),
            audioLanguage = safeDiagnosticValue(audio?.language),
        )
    }

    private fun selectedFormat(tracks: Tracks, trackType: Int): Format? {
        for (group in tracks.groups) {
            if (group.type != trackType) continue
            for (trackIndex in 0 until group.length) {
                if (group.isTrackSelected(trackIndex)) {
                    return group.getTrackFormat(trackIndex)
                }
            }
        }
        return null
    }

    private fun updateDiagnostics(
        transform: (PlaybackDiagnostics) -> PlaybackDiagnostics,
    ) {
        mutableTrackState.value = mutableTrackState.value.copy(
            diagnostics = transform(mutableTrackState.value.diagnostics),
        )
    }

    private fun ensureSupportedAudioSelected(tracks: Tracks) {
        val hasSelectedAudio = tracks.groups.any { group ->
            group.type == C.TRACK_TYPE_AUDIO &&
                (0 until group.length).any { index -> group.isTrackSelected(index) }
        }
        if (hasSelectedAudio) return

        val fallback = tracks.groups.firstNotNullOfOrNull { group ->
            if (group.type != C.TRACK_TYPE_AUDIO) {
                null
            } else {
                val index = (0 until group.length).firstOrNull { index -> group.isTrackSupported(index) }
                index?.let { supportedIndex -> group.mediaTrackGroup to supportedIndex }
            }
        } ?: return

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(
                TrackSelectionOverride(
                    fallback.first,
                    fallback.second,
                ),
            )
            .build()
    }

    private fun resetTrackSelectionForNewMedia() {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
    }

    private fun clearTrackSession() {
        nextTrackId = 1L
        trackIdsByKey.clear()
        trackHandlesById.clear()
        mutableTrackState.value = PlaybackTrackSelectionPolicy.resetForNewMedia()
    }

    private fun runOnPlayerThread(action: () -> Unit) {
        if (Looper.myLooper() == player.applicationLooper) {
            action()
        } else {
            playerHandler.post(action)
        }
    }
}

private fun PlaybackDiagnostics.withCurrentVideoFormat(format: Format): PlaybackDiagnostics = copy(
    videoMimeType = safeDiagnosticValue(format.sampleMimeType),
    videoCodecs = safeDiagnosticValue(format.codecs),
    videoWidth = format.width.positiveOrNull(),
    videoHeight = format.height.positiveOrNull(),
)

private fun Int.positiveOrNull(): Int? = takeIf { it > 0 }

private fun safeDiagnosticValue(raw: String?): String? {
    val normalized = raw
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(120)
        ?.takeIf(String::isNotBlank)
        ?: return null
    val lower = normalized.lowercase()
    val sensitiveMarker = listOf(
        "://",
        "password=",
        "passwd=",
        "token=",
        "username=",
        "authorization=",
        "bearer ",
    ).any(lower::contains)
    return normalized.takeUnless { sensitiveMarker }
}

private data class Media3TrackKey(
    val trackGroup: TrackGroup,
    val trackIndex: Int,
)

private data class Media3TrackHandle(
    val kind: PlaybackTrackKind,
    val trackGroup: TrackGroup,
    val trackIndex: Int,
    val supported: Boolean,
)

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

data class Media3PlaybackComponents(
    val controller: PlaybackController,
    val videoOutput: PlaybackVideoOutput,
    val trackController: PlaybackTrackController,
)

object Media3PlaybackControllerFactory {
    fun create(
        context: Context,
        resolver: LivePlaybackResolver,
    ): Media3PlaybackComponents {
        val engine = Media3PlaybackEngine(context)
        return Media3PlaybackComponents(
            controller = PlaybackController(
                resolveLocator = resolver::resolve,
                engine = engine,
            ),
            videoOutput = engine,
            trackController = engine,
        )
    }
}
