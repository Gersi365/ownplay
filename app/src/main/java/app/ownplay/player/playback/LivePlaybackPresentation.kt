package app.ownplay.player.playback

/**
 * Describes why Live playback entered the dedicated fullscreen surface.
 *
 * USER keeps fullscreen active until the user explicitly exits it.
 * ROTATION lets a Smartphone return to Preview when the device rotates back to portrait.
 */
enum class LiveFullscreenEntryReason {
    USER,
    ROTATION,
}

/**
 * Single transition policy shared by portrait, landscape, and TV Live playback.
 * Device-specific input only decides whether rotation is allowed to request fullscreen;
 * the playback transition itself remains the same safe restart path everywhere.
 */
object LivePlaybackPresentationPolicy {
    fun shouldEnterFullscreenFromRotation(
        rotationFullscreenEnabled: Boolean,
        isLandscape: Boolean,
        hasSelection: Boolean,
        alreadyFullscreen: Boolean,
    ): Boolean =
        rotationFullscreenEnabled &&
            isLandscape &&
            hasSelection &&
            !alreadyFullscreen

    fun shouldReturnToPreviewFromRotation(
        rotationFullscreenEnabled: Boolean,
        isPortrait: Boolean,
        entryReason: LiveFullscreenEntryReason?,
        isFullscreen: Boolean,
    ): Boolean =
        rotationFullscreenEnabled &&
            isPortrait &&
            isFullscreen &&
            entryReason == LiveFullscreenEntryReason.ROTATION
}
