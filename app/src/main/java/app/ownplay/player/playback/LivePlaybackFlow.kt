package app.ownplay.player.playback

import app.ownplay.player.live.LiveChannelItem

data class LivePlaybackBrowseEntry(
    val channelId: String,
    val displayName: String,
) {
    init {
        require(channelId.isNotBlank()) { "Channel ID must not be blank" }
    }

    override fun toString(): String =
        "LivePlaybackBrowseEntry(channelId=<opaque>, displayName=$displayName)"
}

data class LivePlaybackBrowseContext(
    val sourceId: String,
    val entries: List<LivePlaybackBrowseEntry>,
) {
    init {
        require(sourceId.isNotBlank()) { "Source ID must not be blank" }
        require(entries.map(LivePlaybackBrowseEntry::channelId).distinct().size == entries.size) {
            "Browse context channel IDs must be unique"
        }
    }

    fun selectionFor(channelId: String): LivePlaybackSelection? {
        val index = entries.indexOfFirst { entry -> entry.channelId == channelId }
        if (index < 0) return null
        val entry = entries[index]
        return LivePlaybackSelection(
            request = PlaybackRequest(
                sourceId = sourceId,
                channelId = entry.channelId,
                navigationContext = PlaybackNavigationContext(
                    previousChannelId = entries.getOrNull(index - 1)?.channelId,
                    nextChannelId = entries.getOrNull(index + 1)?.channelId,
                ),
            ),
            displayName = entry.displayName,
            browseContext = this,
        )
    }

    override fun toString(): String =
        "LivePlaybackBrowseContext(sourceId=<opaque>, entryCount=${entries.size})"

    companion object {
        fun capture(
            sourceId: String,
            visibleChannels: List<LiveChannelItem>,
        ): LivePlaybackBrowseContext = LivePlaybackBrowseContext(
            sourceId = sourceId,
            entries = visibleChannels.asSequence()
                .filter { channel -> channel.sourceId == sourceId }
                .distinctBy(LiveChannelItem::channelId)
                .map { channel ->
                    LivePlaybackBrowseEntry(
                        channelId = channel.channelId,
                        displayName = normalizedDisplayName(channel.displayName),
                    )
                }
                .toList(),
        )
    }
}

data class LivePlaybackSelection(
    val request: PlaybackRequest,
    val displayName: String,
    private val browseContext: LivePlaybackBrowseContext? = null,
) {
    init {
        require(request.mediaKind == PlaybackMediaKind.LIVE) {
            "Live playback selection requires LIVE media kind"
        }
    }

    fun navigate(direction: PlaybackNavigationDirection): LivePlaybackSelection? {
        val targetChannelId = request.navigationTarget(direction) ?: return null
        return browseContext?.selectionFor(targetChannelId)
    }

    override fun toString(): String =
        "LivePlaybackSelection(request=$request, displayName=$displayName)"

    companion object {
        fun from(channel: LiveChannelItem): LivePlaybackSelection = LivePlaybackSelection(
            request = PlaybackRequest(
                sourceId = channel.sourceId,
                channelId = channel.channelId,
            ),
            displayName = normalizedDisplayName(channel.displayName),
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
        browseContext: LivePlaybackBrowseContext? = null,
    ): LiveChannelSelectionAction = if (isEditing) {
        LiveChannelSelectionAction.ToggleEditSelection(channel.channelId)
    } else {
        LiveChannelSelectionAction.StartPlayback(
            selection = browseContext
                ?.takeIf { context -> context.sourceId == channel.sourceId }
                ?.selectionFor(channel.channelId)
                ?: LivePlaybackSelection.from(channel),
        )
    }
}

private fun normalizedDisplayName(value: String): String =
    value.trim().ifBlank { "Live channel" }
