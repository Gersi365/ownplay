package app.ownplay.player.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface PlaybackConnectivityMonitor : AutoCloseable {
    val state: StateFlow<PlaybackNetworkState>
}

class AndroidPlaybackConnectivityMonitor(
    context: Context,
) : PlaybackConnectivityMonitor {
    private val connectivityManager = requireNotNull(
        context.applicationContext.getSystemService(ConnectivityManager::class.java),
    )
    private val mutableState = MutableStateFlow(currentState())
    private var registered = false

    override val state: StateFlow<PlaybackNetworkState> = mutableState.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mutableState.value = PlaybackNetworkState.AVAILABLE
        }

        override fun onLost(network: Network) {
            mutableState.value = PlaybackNetworkState.UNAVAILABLE
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(callback)
        registered = true
    }

    override fun close() {
        if (!registered) return
        registered = false
        runCatching {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    private fun currentState(): PlaybackNetworkState =
        if (connectivityManager.activeNetwork != null) {
            PlaybackNetworkState.AVAILABLE
        } else {
            PlaybackNetworkState.UNAVAILABLE
        }
}
