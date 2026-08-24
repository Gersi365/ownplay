package app.ownplay.player.playback

private const val LOCAL_VIDEO_SOURCE_ID = "local-video"

object LocalVideoPlayback {
    fun request(contentUri: String): PlaybackRequest {
        val normalized = normalizeContentUri(contentUri)
            ?: throw IllegalArgumentException("Local video URI must use content://")
        return PlaybackRequest(
            sourceId = LOCAL_VIDEO_SOURCE_ID,
            channelId = normalized,
            mediaKind = PlaybackMediaKind.LOCAL_VIDEO,
        )
    }

    fun resolve(request: PlaybackRequest): ResolvedPlaybackLocator? {
        if (request.mediaKind != PlaybackMediaKind.LOCAL_VIDEO) return null
        val normalized = normalizeContentUri(request.channelId) ?: return null
        return ResolvedPlaybackLocator(
            value = normalized,
            origin = ResolvedPlaybackOrigin.LOCAL_DOWNLOAD,
        )
    }

    internal fun normalizeContentUri(raw: String): String? {
        val normalized = raw.trim()
        if (normalized.isBlank()) return null
        if (!normalized.startsWith("content://", ignoreCase = true)) return null
        if (normalized.any(Char::isWhitespace)) return null
        return normalized
    }
}
