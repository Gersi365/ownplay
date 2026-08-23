package app.ownplay.player.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.persistence.PlaylistSourceSummary
import app.ownplay.player.persistence.SourceKinds
import app.ownplay.player.source.SourceError
import app.ownplay.player.source.SourceSyncStage
import app.ownplay.player.source.SourceSyncState
import app.ownplay.player.source.management.SourceEditSnapshot
import app.ownplay.player.source.management.SourceMutationFailure
import app.ownplay.player.source.management.SourceMutationResult
import app.ownplay.player.source.onboarding.SourceOnboardingFailure
import app.ownplay.player.source.onboarding.SourceOnboardingResult
import kotlinx.coroutines.launch

private enum class AddPlaylistMode { XTREAM, REMOTE_M3U, LOCAL_M3U }

@Composable
internal fun PlaylistSettingsScreen(
    runtime: OwnPlayAppRuntime,
    summaries: List<PlaylistSourceSummary>,
    syncState: SourceSyncState,
    onOpenInLive: (String) -> Unit,
) {
    var addMode by remember { mutableStateOf<AddPlaylistMode?>(null) }
    var editSnapshot by remember { mutableStateOf<SourceEditSnapshot?>(null) }
    var deleteTarget by remember { mutableStateOf<PlaylistSourceSummary?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Playlists",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${summaries.size} configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = { addMode = AddPlaylistMode.XTREAM }) {
                Text("Add playlist")
            }
        }

        sourceSyncStatus(syncState)?.let { status ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (
                        syncState.stage == SourceSyncStage.LoadingChannels ||
                        syncState.stage == SourceSyncStage.LoadingEpg
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        actionError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (summaries.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("No playlists yet", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Add an Xtream or M3U source. Live stays available while channels are imported.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { addMode = AddPlaylistMode.XTREAM }) {
                        Text("Add playlist")
                    }
                }
            }
        }

        summaries.forEach { summary ->
            PlaylistCard(
                summary = summary,
                syncState = syncState,
                onOpen = { onOpenInLive(summary.sourceId) },
                onRefresh = {
                    scope.launch {
                        runtime.refreshSource(summary.sourceId)
                    }
                },
                onEdit = {
                    scope.launch {
                        val loaded = runtime.loadSourceEditSnapshot(summary.sourceId)
                        if (loaded == null) {
                            actionError = "Could not load playlist settings."
                        } else {
                            actionError = null
                            editSnapshot = loaded
                        }
                    }
                },
                onDelete = { deleteTarget = summary },
            )
        }

        HorizontalDivider()
        Text(
            text = "Add source type",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { addMode = AddPlaylistMode.XTREAM },
                modifier = Modifier.weight(1f),
            ) { Text("Xtream") }
            OutlinedButton(
                onClick = { addMode = AddPlaylistMode.REMOTE_M3U },
                modifier = Modifier.weight(1f),
            ) { Text("M3U URL") }
            OutlinedButton(
                onClick = { addMode = AddPlaylistMode.LOCAL_M3U },
                modifier = Modifier.weight(1f),
            ) { Text("File") }
        }
    }

    addMode?.let { mode ->
        AddPlaylistDialog(
            mode = mode,
            runtime = runtime,
            onDismiss = { addMode = null },
            onCompleted = {
                addMode = null
                actionError = null
            },
        )
    }

    editSnapshot?.let { snapshot ->
        EditPlaylistDialog(
            snapshot = snapshot,
            runtime = runtime,
            onDismiss = { editSnapshot = null },
            onSaved = {
                editSnapshot = null
                actionError = null
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete playlist?") },
            text = {
                Text(
                    "${target.name} and its imported channel catalog will be removed from OwnPlay."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            when (val result = runtime.deleteSource(target.sourceId)) {
                                SourceMutationResult.Success -> {
                                    deleteTarget = null
                                    actionError = null
                                }
                                is SourceMutationResult.Failure -> {
                                    actionError = mutationFailureMessage(result.reason)
                                    deleteTarget = null
                                }
                            }
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PlaylistCard(
    summary: PlaylistSourceSummary,
    syncState: SourceSyncState,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val isSyncing = syncState.sourceId == summary.sourceId &&
        (syncState.stage == SourceSyncStage.LoadingChannels ||
            syncState.stage == SourceSyncStage.LoadingEpg)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${sourceKindLabel(summary.sourceKind)} • ${summary.channelCount} channels",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isSyncing) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(onClick = onOpen) { Text("Live") }
                TextButton(onClick = onRefresh, enabled = !isSyncing) { Text("Refresh") }
                TextButton(onClick = onEdit, enabled = !isSyncing) { Text("Edit") }
                TextButton(onClick = onDelete, enabled = !isSyncing) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun AddPlaylistDialog(
    mode: AddPlaylistMode,
    runtime: OwnPlayAppRuntime,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(mode) { mutableStateOf("") }
    var endpoint by remember(mode) { mutableStateOf("") }
    var username by remember(mode) { mutableStateOf("") }
    var password by remember(mode) { mutableStateOf("") }
    var allowCleartext by remember(mode) { mutableStateOf(false) }
    var localUri by remember(mode) { mutableStateOf<String?>(null) }
    var working by remember(mode) { mutableStateOf(false) }
    var error by remember(mode) { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            localUri = uri.toString()
            if (name.isBlank()) name = "Local playlist"
        }
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Add playlist") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = when (mode) {
                            AddPlaylistMode.XTREAM -> "Xtream"
                            AddPlaylistMode.REMOTE_M3U -> "M3U URL"
                            AddPlaylistMode.LOCAL_M3U -> "Local M3U"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (mode) {
                    AddPlaylistMode.XTREAM -> {
                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = { endpoint = it },
                            label = { Text("Server URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = allowCleartext,
                                onCheckedChange = { allowCleartext = it },
                            )
                            Text("Allow HTTP for this provider")
                        }
                    }
                    AddPlaylistMode.REMOTE_M3U -> {
                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = { endpoint = it },
                            label = { Text("Playlist URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    AddPlaylistMode.LOCAL_M3U -> {
                        OutlinedButton(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (localUri == null) "Choose file" else "File selected")
                        }
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (working) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text("Loading channels…")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (working) return@Button
                    working = true
                    error = null
                    scope.launch {
                        val result = when (mode) {
                            AddPlaylistMode.XTREAM -> runtime.addXtreamSource(
                                name = name,
                                serverUrl = endpoint,
                                username = username,
                                password = password,
                                allowCleartext = allowCleartext,
                            )
                            AddPlaylistMode.REMOTE_M3U -> runtime.addRemoteM3uSource(
                                name = name,
                                playlistUrl = endpoint,
                            )
                            AddPlaylistMode.LOCAL_M3U -> {
                                val uri = localUri
                                if (uri == null) {
                                    working = false
                                    error = "Choose a local M3U file."
                                    return@launch
                                }
                                runtime.addLocalM3uSource(name, uri)
                            }
                        }
                        when (result) {
                            is SourceOnboardingResult.Success -> onCompleted()
                            is SourceOnboardingResult.Failure -> {
                                working = false
                                error = onboardingFailureMessage(result.reason)
                            }
                        }
                    }
                },
                enabled = !working,
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") }
        },
    )
}

@Composable
private fun EditPlaylistDialog(
    snapshot: SourceEditSnapshot,
    runtime: OwnPlayAppRuntime,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember(snapshot) { mutableStateOf(snapshot.name) }
    var endpoint by remember(snapshot) { mutableStateOf(snapshot.endpoint.orEmpty()) }
    var username by remember(snapshot) { mutableStateOf("") }
    var password by remember(snapshot) { mutableStateOf("") }
    var allowCleartext by remember(snapshot) { mutableStateOf(snapshot.allowCleartext) }
    var working by remember(snapshot) { mutableStateOf(false) }
    var error by remember(snapshot) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Edit playlist") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (snapshot.sourceKind == SourceKinds.XTREAM) {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text("Server URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Leave username/password empty to keep existing credentials.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("New username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("New password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = allowCleartext,
                            onCheckedChange = { allowCleartext = it },
                        )
                        Text("Allow HTTP for this provider")
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (working) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text("Saving and refreshing…")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (working) return@Button
                    working = true
                    scope.launch {
                        val result = if (snapshot.sourceKind == SourceKinds.XTREAM) {
                            runtime.updateXtreamSource(
                                sourceId = snapshot.sourceId,
                                name = name,
                                serverUrl = endpoint,
                                replacementUsername = username,
                                replacementPassword = password,
                                allowCleartext = allowCleartext,
                            )
                        } else {
                            runtime.renameSource(snapshot.sourceId, name)
                        }
                        when (result) {
                            SourceMutationResult.Success -> onSaved()
                            is SourceMutationResult.Failure -> {
                                working = false
                                error = mutationFailureMessage(result.reason)
                            }
                        }
                    }
                },
                enabled = !working,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") }
        },
    )
}

private fun sourceKindLabel(kind: String): String = when (kind) {
    SourceKinds.XTREAM -> "Xtream"
    SourceKinds.REMOTE_M3U -> "M3U URL"
    SourceKinds.LOCAL_M3U -> "Local M3U"
    else -> "Playlist"
}

private fun sourceSyncStatus(state: SourceSyncState): String? = when (state.stage) {
    SourceSyncStage.Idle -> null
    SourceSyncStage.LoadingChannels -> "Loading channels…"
    SourceSyncStage.LoadingEpg -> "Channels loaded. Loading EPG…"
    SourceSyncStage.Ready -> "Ready • ${state.channelCount} channels • ${state.epgChannelCount} EPG channels"
    SourceSyncStage.ChannelsFailed -> "Channel refresh failed. Existing channels were kept."
    SourceSyncStage.EpgFailed -> "Channels are ready. EPG refresh failed."
}

private fun onboardingFailureMessage(failure: SourceOnboardingFailure): String = when (failure) {
    SourceOnboardingFailure.InvalidName -> "Enter a playlist name."
    SourceOnboardingFailure.SecureStorageFailure -> "Secure storage failed."
    SourceOnboardingFailure.PersistenceFailure -> "Could not save the playlist."
    SourceOnboardingFailure.CatalogImportFailure -> "Could not import channels."
    is SourceOnboardingFailure.SourceFailure -> sourceErrorMessage(failure.error)
}

private fun mutationFailureMessage(failure: SourceMutationFailure): String = when (failure) {
    SourceMutationFailure.NotFound -> "Playlist no longer exists."
    SourceMutationFailure.InvalidName -> "Enter a playlist name."
    SourceMutationFailure.UnsupportedEdit -> "This playlist type cannot edit its endpoint yet."
    SourceMutationFailure.IncompleteCredentialReplacement -> "Enter both new username and password, or leave both empty."
    SourceMutationFailure.SecureStorageFailure -> "Secure storage failed."
    SourceMutationFailure.PersistenceFailure -> "Could not save playlist changes."
    is SourceMutationFailure.SourceFailure -> sourceErrorMessage(failure.error)
}

private fun sourceErrorMessage(error: SourceError): String = when (error) {
    SourceError.InvalidUrl -> "Invalid URL."
    SourceError.InvalidCredentials -> "Invalid credentials."
    SourceError.AuthenticationFailed -> "Authentication failed."
    SourceError.NetworkUnavailable -> "Network unavailable."
    SourceError.Timeout -> "Provider timed out."
    SourceError.SecureConnectionFailed -> "Secure connection failed."
    SourceError.MalformedResponse -> "Provider returned an unsupported response."
    SourceError.CleartextTransportRequiresOptIn -> "Enable HTTP for this provider."
    is SourceError.HttpFailure -> "Provider returned HTTP ${error.statusCode}."
    else -> "Could not connect to this source."
}
