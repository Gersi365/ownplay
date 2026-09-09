package app.ownplay.player.ui.rebuild

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import app.ownplay.player.OwnPlayAppRuntime

private sealed interface TvFocusTarget {
    data class Rail(val destination: TvDestination) : TvFocusTarget
    data object Content : TvFocusTarget
}

private data class TvFocusRequest(
    val sequence: Long,
    val target: TvFocusTarget,
)

@Composable
internal fun RebuiltOwnPlayTvApp(
    runtime: OwnPlayAppRuntime,
    onPlaybackFullscreenChanged: (Boolean) -> Unit,
    onPlaybackSurfaceActiveChanged: (Boolean) -> Unit,
    onLivePreviewActiveChanged: (Boolean) -> Unit,
) {
    val sources by runtime.observeSourceSummaries().collectAsState(initial = emptyList())
    var destination by remember { mutableStateOf(TvDestination.HOME) }
    val railRequesters = remember {
        TvDestination.entries.associateWith { FocusRequester() }
    }
    val contentEntryRequester = remember { FocusRequester() }
    var focusSequence by remember { mutableLongStateOf(0L) }
    var pendingFocus by remember { mutableStateOf<TvFocusRequest?>(null) }

    fun requestFocus(target: TvFocusTarget) {
        focusSequence += 1L
        pendingFocus = TvFocusRequest(focusSequence, target)
    }

    fun openDestination(target: TvDestination) {
        destination = target
        requestFocus(TvFocusTarget.Content)
    }

    fun returnToRail(target: TvDestination = destination) {
        destination = target
        requestFocus(TvFocusTarget.Rail(target))
    }

    LaunchedEffect(Unit) {
        onPlaybackFullscreenChanged(false)
        onPlaybackSurfaceActiveChanged(false)
        onLivePreviewActiveChanged(false)
        requestFocus(TvFocusTarget.Rail(TvDestination.HOME))
    }

    LaunchedEffect(pendingFocus) {
        val request = pendingFocus ?: return@LaunchedEffect
        withFrameNanos { }
        when (val target = request.target) {
            TvFocusTarget.Content -> contentEntryRequester.requestFocus()
            is TvFocusTarget.Rail -> railRequesters.getValue(target.destination).requestFocus()
        }
    }

    BackHandler(enabled = destination != TvDestination.HOME) {
        returnToRail(TvDestination.HOME)
    }

    OwnPlayRebuildTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = RebuildBackground,
        ) {
            Row(Modifier.fillMaxSize()) {
                TvNavigationRail(
                    selected = destination,
                    requesters = railRequesters,
                    onSelected = ::openDestination,
                    onEnterContent = ::openDestination,
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    when (destination) {
                        TvDestination.HOME -> TvHomeFoundationScreen(
                            sources = sources,
                            entryRequester = contentEntryRequester,
                            onNavigate = ::openDestination,
                            onReturnToRail = { returnToRail(TvDestination.HOME) },
                        )
                        TvDestination.LIVE -> TvCatalogFoundationScreen(
                            destination = TvDestination.LIVE,
                            sources = sources,
                            entryRequester = contentEntryRequester,
                            onOpenSettings = { openDestination(TvDestination.SETTINGS) },
                            onReturnToRail = { returnToRail(TvDestination.LIVE) },
                        )
                        TvDestination.MOVIES -> TvCatalogFoundationScreen(
                            destination = TvDestination.MOVIES,
                            sources = sources,
                            entryRequester = contentEntryRequester,
                            onOpenSettings = { openDestination(TvDestination.SETTINGS) },
                            onReturnToRail = { returnToRail(TvDestination.MOVIES) },
                        )
                        TvDestination.SERIES -> TvCatalogFoundationScreen(
                            destination = TvDestination.SERIES,
                            sources = sources,
                            entryRequester = contentEntryRequester,
                            onOpenSettings = { openDestination(TvDestination.SETTINGS) },
                            onReturnToRail = { returnToRail(TvDestination.SERIES) },
                        )
                        TvDestination.SETTINGS -> TvSettingsFoundationScreen(
                            sources = sources,
                            entryRequester = contentEntryRequester,
                            onReturnToRail = { returnToRail(TvDestination.SETTINGS) },
                        )
                    }
                }
            }
        }
    }
}
