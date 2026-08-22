package app.ownplay.player.playback

import app.ownplay.player.live.LiveChannelItem

data class LivePlaybackSelection(
    val request: PlaybackRequest,
    val displayName: String,
) {
    init {
        require(request.mediaKind == PlaybackMediaKind.LIVE) {
            "Live playback selection requires LIVE media kind"
        }
    }

    override fun toString(): String =
        "LivePlaybackSelection(request=$request, displayName=$displayName)"

    companion object {
        fun from(channel: LiveChannelItem): LivePlaybackSelection = LivePlaybackSelection(
            request = PlaybackRequest(
                sourceId = channel.sourceId,
                channelId = channel.channelId,
            ),
            displayName = channel.displayName.trim().ifBlank { "Live channel" },
        )
    }
}

sealed interface LiveChannelSelectionAction {
    data class StartPlayback(
        val selection: LivePlaybackSelection,
    ) : LiveChannelSelectionAction

    data class ToggleEditSelection(
        val channelId: String,
    ) : LiveChannelSelectionAction {
        override fun toString(): String = "ToggleEditSelection(channelId=<opaque>)"
    }
}

object LiveChannelSelectionRouter {
    fun route(
        channel: LiveChannelItem,
        isEditing: Boolean,
    ): LiveChannelSelectionAction = if (isEditing) {
        LiveChannelSelectionAction.ToggleEditSelection(channel.channelId)
    } else {
        LiveChannelSelectionAction.StartPlayback(LivePlaybackSelection.from(channel))
    }
}
