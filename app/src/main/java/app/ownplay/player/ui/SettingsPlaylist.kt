package app.ownplay.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.persistence.PlaylistSourceSummary
import app.ownplay.player.source.SourceSyncState
import app.ownplay.player.target.OwnPlayBuildTarget

@Composable
internal fun PlaylistManagementSubscreen(
    runtime: OwnPlayAppRuntime,
    summaries: List<PlaylistSourceSummary>,
    syncState: SourceSyncState,
    onBack: () -> Unit,
    onOpenInLive: (String) -> Unit,
    focusBackOnEntry: Boolean = false,
) {
    val backFocusRequester = remember { FocusRequester() }

    LaunchedEffect(focusBackOnEntry) {
        if (OwnPlayBuildTarget.usesDpad && focusBackOnEntry) {
            backFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.focusRequester(backFocusRequester),
                ) { Text("‹ Settings") }
                Text(
                    text = "Playlists",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            PlaylistSettingsScreen(
                runtime = runtime,
                summaries = summaries,
                syncState = syncState,
                onOpenInLive = onOpenInLive,
            )
        }
    }
}

