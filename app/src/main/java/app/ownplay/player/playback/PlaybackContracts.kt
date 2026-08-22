package app.ownplay.player.playback

enum class PlaybackMediaKind {
    LIVE,
}

enum class PlaybackNavigationDirection {
    PREVIOUS,
    NEXT,
}

data class PlaybackNavigationContext(
    val previousChannelId: String?,
    val nextChannelId: String?,
) {
    init {
        require(previousChannelId == null || previousChannelId.isNotBlank()) {
            "Previous channel ID must be null or non-blank"
        }
        require(nextChannelId == null || nextChannelId.isNotBlank()) {
            "Next channel ID must be null or non-blank"
        }
    }

    fun target(direction: PlaybackNavigationDirection): String? = when (direction) {
        PlaybackNavigationDirection.PREVIOUS -> previousChannelId
        PlaybackNavigationDirection.NEXT -> nextChannelId
    }

    override fun toString(): String =
        "PlaybackNavigationContext(previousChannelId=${opaquePresence(previousChannelId)}, " +
            "nextChannelId=${opaquePresence(nextChannelId)})"

    private fun opaquePresence(value: String?): String = if (value == null) "null" else "<opaque>"
}

data class PlaybackRequest(
    val sourceId: String,
    val channelId: String,
    val mediaKind: PlaybackMediaKind = PlaybackMediaKind.LIVE,
    val navigationContext: PlaybackNavigationContext? = null,
) {
    init {
        require(sourceId.isNotBlank()) { "Source ID must not be blank" }
        require(channelId.isNotBlank()) { "Channel ID must not be blank" }
        require(navigationContext?.previousChannelId != channelId) {
            "Previous channel must differ from the current channel"
        }
        require(navigationContext?.nextChannelId != channelId) {
            "Next channel must differ from the current channel"
        }
    }

    fun navigationTarget(direction: PlaybackNavigationDirection): String? =
        navigationContext?.target(direction)

    override fun toString(): String =
        "PlaybackRequest(sourceId=<opaque>, channelId=<opaque>, mediaKind=$mediaKind, " +
            "navigationContext=$navigationContext)"
}

enum class PlaybackFailureCategory(
    val retryable: Boolean,
) {
    NETWORK_UNAVAILABLE(retryable = true),
    TIMEOUT(retryable = true),
    AUTHENTICATION_FAILURE(retryable = false),
    STREAM_UNAVAILABLE(retryable = true),
    UNSUPPORTED_MEDIA(retryable = false),
    UNKNOWN(retryable = false),
}

data class PlaybackFailure(
    val category: PlaybackFailureCategory,
) {
    val retryable: Boolean
        get() = category.retryable
}

sealed interface PlaybackState {
    data object Idle : PlaybackState

    data class Loading(
        val request: PlaybackRequest,
    ) : PlaybackState

    data class Playing(
        val request: PlaybackRequest,
    ) : PlaybackState

    data class Paused(
        val request: PlaybackRequest,
    ) : PlaybackState

    data class Failed(
        val request: PlaybackRequest,
        val failure: PlaybackFailure,
    ) : PlaybackState
}

sealed interface PlaybackEvent {
    data class Start(
        val request: PlaybackRequest,
    ) : PlaybackEvent

    data object Prepared : PlaybackEvent
    data object Play : PlaybackEvent
    data object Pause : PlaybackEvent

    data class Fail(
        val failure: PlaybackFailure,
    ) : PlaybackEvent

    data object Retry : PlaybackEvent
    data object Stop : PlaybackEvent
}

object PlaybackReducer {
    fun reduce(
        state: PlaybackState,
        event: PlaybackEvent,
    ): PlaybackState = when (event) {
        is PlaybackEvent.Start -> PlaybackState.Loading(event.request)
        PlaybackEvent.Prepared -> when (state) {
            is PlaybackState.Loading -> PlaybackState.Playing(state.request)
            else -> state
        }
        PlaybackEvent.Play -> when (state) {
            is PlaybackState.Paused -> PlaybackState.Playing(state.request)
            else -> state
        }
        PlaybackEvent.Pause -> when (state) {
            is PlaybackState.Playing -> PlaybackState.Paused(state.request)
            else -> state
        }
        is PlaybackEvent.Fail -> state.requestOrNull()
            ?.let { request -> PlaybackState.Failed(request, event.failure) }
            ?: state
        PlaybackEvent.Retry -> when (state) {
            is PlaybackState.Failed -> if (state.failure.retryable) {
                PlaybackState.Loading(state.request)
            } else {
                state
            }
            else -> state
        }
        PlaybackEvent.Stop -> PlaybackState.Idle
    }

    private fun PlaybackState.requestOrNull(): PlaybackRequest? = when (this) {
        PlaybackState.Idle -> null
        is PlaybackState.Loading -> request
        is PlaybackState.Playing -> request
        is PlaybackState.Paused -> request
        is PlaybackState.Failed -> request
    }
}
