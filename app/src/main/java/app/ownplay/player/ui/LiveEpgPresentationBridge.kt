package app.ownplay.player.ui

import app.ownplay.player.epg.EpgSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ephemeral UI-only handoff between Preview EPG and Live fullscreen presentation.
 *
 * This bridge deliberately stores no provider credentials, stream URLs or persistent user data.
 * It also owns no repository/player lifecycle; it only mirrors the EPG snapshot already loaded by
 * LiveRoute and carries a one-shot request to reopen the full guide after fullscreen returns to
 * Preview.
 */
internal object LiveEpgPresentationBridge {
    private val _snapshot = MutableStateFlow<EpgSnapshot?>(null)
    val snapshot: StateFlow<EpgSnapshot?> = _snapshot.asStateFlow()

    @Volatile
    private var fullGuideRequested = false

    fun publish(snapshot: EpgSnapshot?) {
        _snapshot.value = snapshot
    }

    fun requestFullGuide() {
        fullGuideRequested = true
    }

    @Synchronized
    fun consumeFullGuideRequest(): Boolean {
        if (!fullGuideRequested) return false
        fullGuideRequested = false
        return true
    }
}
