package app.ownplay.player.playback

private const val LOCAL_VIDEO_SOURCE_ID = "local-video"
private const val PLATFORM_CACHE_RESERVE_BYTES = 64L * 1024L * 1024L
private val PLATFORM_CACHE_EXTENSION = Regex("[A-Za-z0-9]{1,10}")

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

    fun shouldUsePlatformFallback(failure: PlaybackFailure): Boolean =
        failure.category == PlaybackFailureCategory.UNSUPPORTED_MEDIA

    internal fun platformCacheBudget(usableSpaceBytes: Long): Long =
        (usableSpaceBytes - PLATFORM_CACHE_RESERVE_BYTES).coerceAtLeast(0L)

    internal fun platformCacheSuffix(displayName: String): String {
        val extension = displayName
            .substringAfterLast('.', missingDelimiterValue = "")
            .trim()
            .takeIf { PLATFORM_CACHE_EXTENSION.matches(it) }
            ?.lowercase()
        return if (extension == null) ".video" else ".$extension"
    }

    internal fun normalizeContentUri(raw: String): String? {
        val normalized = raw.trim()
        if (normalized.isBlank()) return null
        if (!normalized.startsWith("content://", ignoreCase = true)) return null
        if (normalized.any(Char::isWhitespace)) return null
        return normalized
    }
}
