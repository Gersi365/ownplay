package app.ownplay.player.ui.tv

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import app.ownplay.player.source.SourceValidator
import app.ownplay.player.source.UrlValidationResult
import app.ownplay.player.source.management.SourceEditSnapshot
import app.ownplay.player.source.management.SourceMutationFailure
import app.ownplay.player.source.management.SourceMutationResult
import app.ownplay.player.source.selection.ActivePlaylistSelection
import app.ownplay.player.source.selection.ActivePlaylistStore
import app.ownplay.player.ui.SourceSubmissionCoordinator
import app.ownplay.player.ui.sourceErrorMessage
import app.ownplay.player.ui.sourceSyncStatus
import kotlinx.coroutines.launch

private const val ADD_PLAYLIST_FOCUS_KEY = "__add_playlist__"

private sealed interface TvPlaylistPage {
    data object List : TvPlaylistPage
    data object AddType : TvPlaylistPage
    data class Add(val mode: TvPlaylistAddMode) : TvPlaylistPage
    data class Detail(val sourceId: String) : TvPlaylistPage
    data class Edit(val snapshot: SourceEditSnapshot) : TvPlaylistPage
}

private enum class TvPlaylistAddMode(
    val title: String,
    val description: String,
) {
    XTREAM(
        title = "Xtream",
        description = "Server URL plus provider username and password.",
    ),
    REMOTE_M3U(
        title = "M3U URL",
        description = "A remote M3U or M3U8 playlist URL.",
    ),
    LOCAL_M3U(
        title = "Local file",
        description = "Choose an M3U or M3U8 document available to Android TV.",
    ),
}

private enum class TvPlaylistDetailAction {
    OPEN_LIVE,
    REFRESH,
    EDIT,
    ACTIVATE,
    DELETE,
}

@Composable
internal fun TvPlaylistSettingsScreen(
    runtime: OwnPlayAppRuntime,
    summaries: List<PlaylistSourceSummary>,
    syncState: SourceSyncState,
    onBack: () -> Unit,
    onOpenInLive: (String) -> Unit,
) {
    var page by remember { mutableStateOf<TvPlaylistPage>(TvPlaylistPage.List) }
    var listRestoreKey by remember { mutableStateOf(ADD_PLAYLIST_FOCUS_KEY) }
    var addTypeRestoreMode by remember { mutableStateOf(TvPlaylistAddMode.XTREAM) }
    var detailRestoreAction by remember { mutableStateOf(TvPlaylistDetailAction.OPEN_LIVE) }
    val sourceSyncStates by runtime.sourceSyncStates.collectAsState()

    BackHandler {
        when (val current = page) {
            TvPlaylistPage.List -> onBack()
            TvPlaylistPage.AddType -> {
                listRestoreKey = ADD_PLAYLIST_FOCUS_KEY
                page = TvPlaylistPage.List
            }
            is TvPlaylistPage.Add -> {
                addTypeRestoreMode = current.mode
                page = TvPlaylistPage.AddType
            }
            is TvPlaylistPage.Detail -> {
                listRestoreKey = current.sourceId
                page = TvPlaylistPage.List
            }
            is TvPlaylistPage.Edit -> {
                detailRestoreAction = TvPlaylistDetailAction.EDIT
                page = TvPlaylistPage.Detail(current.snapshot.sourceId)
            }
        }
    }

    when (val current = page) {
        TvPlaylistPage.List -> TvPlaylistListPage(
            summaries = summaries,
            sourceSyncStates = sourceSyncStates,
            syncState = syncState,
            restoreKey = listRestoreKey,
            onBack = onBack,
            onAdd = {
                addTypeRestoreMode = TvPlaylistAddMode.XTREAM
                page = TvPlaylistPage.AddType
            },
            onOpenSource = { sourceId ->
                listRestoreKey = sourceId
                detailRestoreAction = TvPlaylistDetailAction.OPEN_LIVE
                page = TvPlaylistPage.Detail(sourceId)
            },
        )

        TvPlaylistPage.AddType -> TvPlaylistAddTypePage(
            restoreMode = addTypeRestoreMode,
            onBack = {
                listRestoreKey = ADD_PLAYLIST_FOCUS_KEY
                page = TvPlaylistPage.List
            },
            onChoose = { mode ->
                addTypeRestoreMode = mode
                page = TvPlaylistPage.Add(mode)
            },
        )

        is TvPlaylistPage.Add -> TvPlaylistAddForm(
            mode = current.mode,
            runtime = runtime,
            onBack = {
                addTypeRestoreMode = current.mode
                page = TvPlaylistPage.AddType
            },
            onCompleted = {
                listRestoreKey = ADD_PLAYLIST_FOCUS_KEY
                page = TvPlaylistPage.List
            },
        )

        is TvPlaylistPage.Detail -> {
            val summary = summaries.firstOrNull { it.sourceId == current.sourceId }
            if (summary == null) {
                LaunchedEffect(current.sourceId) {
                    listRestoreKey = ADD_PLAYLIST_FOCUS_KEY
                    page = TvPlaylistPage.List
                }
            } else {
                TvPlaylistDetailPage(
                    runtime = runtime,
                    summary = summary,
                    syncState = sourceSyncStates[summary.sourceId],
                    restoreAction = detailRestoreAction,
                    onBack = {
                        listRestoreKey = summary.sourceId
                        page = TvPlaylistPage.List
                    },
                    onOpenInLive = { onOpenInLive(summary.sourceId) },
                    onEdit = { snapshot ->
                        detailRestoreAction = TvPlaylistDetailAction.EDIT
                        page = TvPlaylistPage.Edit(snapshot)
                    },
                    onDeleted = {
                        listRestoreKey = ADD_PLAYLIST_FOCUS_KEY
                        page = TvPlaylistPage.List
                    },
                )
            }
        }

        is TvPlaylistPage.Edit -> TvPlaylistEditForm(
            runtime = runtime,
            snapshot = current.snapshot,
            onBack = {
                detailRestoreAction = TvPlaylistDetailAction.EDIT
                page = TvPlaylistPage.Detail(current.snapshot.sourceId)
            },
            onSaved = {
                detailRestoreAction = TvPlaylistDetailAction.EDIT
                page = TvPlaylistPage.Detail(current.snapshot.sourceId)
            },
        )
    }
}

@Composable
private fun TvPlaylistListPage(
    summaries: List<PlaylistSourceSummary>,
    sourceSyncStates: Map<String, SourceSyncState>,
    syncState: SourceSyncState,
    restoreKey: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenSource: (String) -> Unit,
) {
    val addFocusRequester = remember { FocusRequester() }
    val sourceIds = summaries.map { it.sourceId }
    val sourceFocusRequesters = remember(sourceIds) {
        sourceIds.associateWith { FocusRequester() }
    }
    var focusedKey by remember(sourceIds) {
        mutableStateOf(
            restoreKey.takeIf { it == ADD_PLAYLIST_FOCUS_KEY || it in sourceIds }
                ?: ADD_PLAYLIST_FOCUS_KEY,
        )
    }

    LaunchedEffect(sourceIds, restoreKey) {
        withFrameNanos { }
        val target = restoreKey.takeIf { it in sourceIds }
        if (target != null) {
            sourceFocusRequesters[target]?.requestFocus()
        } else {
            addFocusRequester.requestFocus()
        }
    }

    TvPlaylistPageScaffold(
        title = "Playlists",
        description = "Choose a source to manage it, or add a new source.",
        onBack = onBack,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(390.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TvPlaylistNavigationRow(
                    title = "Add playlist",
                    detail = "Xtream, M3U URL or local file",
                    focused = focusedKey == ADD_PLAYLIST_FOCUS_KEY,
                    focusRequester = addFocusRequester,
                    onFocused = { focusedKey = ADD_PLAYLIST_FOCUS_KEY },
                    onClick = onAdd,
                )

                summaries.forEach { summary ->
                    TvPlaylistNavigationRow(
                        title = summary.name,
                        detail = playlistSummaryStatus(
                            summary = summary,
                            syncState = sourceSyncStates[summary.sourceId],
                        ),
                        focused = focusedKey == summary.sourceId,
                        focusRequester = sourceFocusRequesters.getValue(summary.sourceId),
                        onFocused = { focusedKey = summary.sourceId },
                        onClick = { onOpenSource(summary.sourceId) },
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 30.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (focusedKey == ADD_PLAYLIST_FOCUS_KEY) {
                        Text(
                            text = "Add playlist",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Add a provider or playlist through a dedicated TV flow. Text entry uses the Android TV keyboard.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        sourceSyncStatus(syncState)?.let { status ->
                            Text(
                                text = status,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        val summary = summaries.firstOrNull { it.sourceId == focusedKey }
                        if (summary != null) {
                            Text(
                                text = summary.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = tvPlaylistSourceKindLabel(summary.sourceKind),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = playlistSummaryStatus(
                                    summary = summary,
                                    syncState = sourceSyncStates[summary.sourceId],
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "Press OK to open source actions.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvPlaylistAddTypePage(
    restoreMode: TvPlaylistAddMode,
    onBack: () -> Unit,
    onChoose: (TvPlaylistAddMode) -> Unit,
) {
    val focusRequesters = remember {
        TvPlaylistAddMode.entries.associateWith { FocusRequester() }
    }
    var focusedMode by remember { mutableStateOf(restoreMode) }

    LaunchedEffect(restoreMode) {
        withFrameNanos { }
        focusRequesters.getValue(restoreMode).requestFocus()
    }

    TvPlaylistPageScaffold(
        title = "Add playlist",
        description = "Choose the source type. OK opens a dedicated setup page.",
        onBack = onBack,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(
                modifier = Modifier.width(390.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TvPlaylistAddMode.entries.forEach { mode ->
                    TvPlaylistNavigationRow(
                        title = mode.title,
                        detail = mode.description,
                        focused = focusedMode == mode,
                        focusRequester = focusRequesters.getValue(mode),
                        onFocused = { focusedMode = mode },
                        onClick = { onChoose(mode) },
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(30.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = focusedMode.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = focusedMode.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvPlaylistDetailPage(
    runtime: OwnPlayAppRuntime,
    summary: PlaylistSourceSummary,
    syncState: SourceSyncState?,
    restoreAction: TvPlaylistDetailAction,
    onBack: () -> Unit,
    onOpenInLive: () -> Unit,
    onEdit: (SourceEditSnapshot) -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val activePlaylistStore = remember(context) {
        ActivePlaylistStore(context.applicationContext)
    }
    val activeSelection by activePlaylistStore.observe().collectAsState(
        initial = ActivePlaylistSelection.Loading,
    )
    val activeSourceId = (activeSelection as? ActivePlaylistSelection.Ready)?.sourceId
    val isActive = summary.enabled && activeSourceId == summary.sourceId
    val scope = rememberCoroutineScope()
    var refreshWorking by remember(summary.sourceId) { mutableStateOf(false) }
    var activateWorking by remember(summary.sourceId) { mutableStateOf(false) }
    var editWorking by remember(summary.sourceId) { mutableStateOf(false) }
    var deleteWorking by remember(summary.sourceId) { mutableStateOf(false) }
    var deleteConfirm by remember(summary.sourceId) { mutableStateOf(false) }
    var restoreDeleteActionFocus by remember(summary.sourceId) { mutableStateOf(false) }
    var error by remember(summary.sourceId) { mutableStateOf<String?>(null) }

    val syncing = syncState?.stage == SourceSyncStage.LoadingChannels ||
        syncState?.stage == SourceSyncStage.LoadingEpg
    val actions = buildList {
        if (summary.enabled) add(TvPlaylistDetailAction.OPEN_LIVE)
        add(TvPlaylistDetailAction.REFRESH)
        if (summary.enabled) add(TvPlaylistDetailAction.EDIT)
        if (summary.enabled && !isActive) add(TvPlaylistDetailAction.ACTIVATE)
        add(TvPlaylistDetailAction.DELETE)
    }
    val actionFocusRequesters = remember(summary.sourceId) {
        TvPlaylistDetailAction.entries.associateWith { FocusRequester() }
    }
    val deleteCancelFocusRequester = remember(summary.sourceId) { FocusRequester() }
    var focusedAction by remember(summary.sourceId) {
        mutableStateOf(restoreAction)
    }

    fun actionEnabled(action: TvPlaylistDetailAction): Boolean = when (action) {
        TvPlaylistDetailAction.OPEN_LIVE -> summary.enabled
        TvPlaylistDetailAction.REFRESH -> !syncing && !refreshWorking
        TvPlaylistDetailAction.EDIT -> summary.enabled && !syncing && !editWorking
        TvPlaylistDetailAction.ACTIVATE -> summary.enabled && !isActive && !activateWorking
        TvPlaylistDetailAction.DELETE -> !deleteWorking
    }

    LaunchedEffect(summary.sourceId, restoreAction) {
        withFrameNanos { }
        val requested = restoreAction.takeIf { it in actions && actionEnabled(it) }
        val target = requested ?: actions.firstOrNull { actionEnabled(it) }
        if (target != null) {
            focusedAction = target
            actionFocusRequesters.getValue(target).requestFocus()
        }
    }

    LaunchedEffect(
        summary.sourceId,
        deleteConfirm,
        deleteWorking,
        restoreDeleteActionFocus,
    ) {
        when {
            deleteConfirm && !deleteWorking -> {
                withFrameNanos { }
                deleteCancelFocusRequester.requestFocus()
            }
            !deleteConfirm && !deleteWorking && restoreDeleteActionFocus -> {
                withFrameNanos { }
                if (
                    TvPlaylistDetailAction.DELETE in actions &&
                    actionEnabled(TvPlaylistDetailAction.DELETE)
                ) {
                    focusedAction = TvPlaylistDetailAction.DELETE
                    actionFocusRequesters
                        .getValue(TvPlaylistDetailAction.DELETE)
                        .requestFocus()
                }
                restoreDeleteActionFocus = false
            }
        }
    }

    TvPlaylistPageScaffold(
        title = summary.name,
        description = playlistSummaryStatus(summary, syncState),
        onBack = onBack,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(
                modifier = Modifier.width(390.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                actions.forEach { action ->
                    TvPlaylistActionRow(
                        title = when (action) {
                            TvPlaylistDetailAction.OPEN_LIVE -> "Open in Live"
                            TvPlaylistDetailAction.REFRESH -> if (summary.enabled) "Refresh" else "Retry import"
                            TvPlaylistDetailAction.EDIT -> "Edit source"
                            TvPlaylistDetailAction.ACTIVATE -> "Use as active playlist"
                            TvPlaylistDetailAction.DELETE -> "Delete playlist"
                        },
                        detail = when (action) {
                            TvPlaylistDetailAction.OPEN_LIVE -> "Open this source in Live browsing."
                            TvPlaylistDetailAction.REFRESH -> if (summary.enabled) {
                                "Refresh channels and supported catalog data."
                            } else {
                                "Retry the pending or failed source import."
                            }
                            TvPlaylistDetailAction.EDIT -> "Edit source name and supported connection details."
                            TvPlaylistDetailAction.ACTIVATE -> "Use this source as the preferred active playlist."
                            TvPlaylistDetailAction.DELETE -> "Remove this source and its imported catalog."
                        },
                        focused = focusedAction == action,
                        enabled = actionEnabled(action),
                        focusRequester = actionFocusRequesters.getValue(action),
                        onFocused = { focusedAction = action },
                        onClick = {
                            error = null
                            when (action) {
                                TvPlaylistDetailAction.OPEN_LIVE -> onOpenInLive()
                                TvPlaylistDetailAction.REFRESH -> {
                                    refreshWorking = true
                                    scope.launch {
                                        try {
                                            if (summary.enabled) {
                                                runtime.refreshSource(summary.sourceId)
                                            } else {
                                                runtime.retryPendingSource(summary.sourceId)
                                            }
                                        } finally {
                                            refreshWorking = false
                                        }
                                    }
                                }
                                TvPlaylistDetailAction.EDIT -> {
                                    editWorking = true
                                    scope.launch {
                                        val snapshot = runtime.loadSourceEditSnapshot(summary.sourceId)
                                        editWorking = false
                                        if (snapshot == null) {
                                            error = "Could not load playlist settings."
                                        } else {
                                            onEdit(snapshot)
                                        }
                                    }
                                }
                                TvPlaylistDetailAction.ACTIVATE -> {
                                    activateWorking = true
                                    scope.launch {
                                        val saved = activePlaylistStore.set(summary.sourceId)
                                        activateWorking = false
                                        if (saved) {
                                            runtime.onActiveSourceSelected(summary.sourceId)
                                        } else {
                                            error = "Could not save the active playlist."
                                        }
                                    }
                                }
                                TvPlaylistDetailAction.DELETE -> {
                                    restoreDeleteActionFocus = false
                                    deleteConfirm = true
                                }
                            }
                        },
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(30.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = tvPlaylistSourceKindLabel(summary.sourceKind),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (isActive) "Active playlist" else playlistSummaryStatus(summary, syncState),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "${summary.channelCount} channels",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    error?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (refreshWorking || activateWorking || editWorking || deleteWorking) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Text("Working…")
                        }
                    }
                }
            }
        }
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = {
                if (!deleteWorking) {
                    restoreDeleteActionFocus = true
                    deleteConfirm = false
                }
            },
            title = { Text("Delete playlist?") },
            text = {
                Text(
                    if (summary.enabled) {
                        "${summary.name} and its imported catalog will be removed from OwnPlay."
                    } else {
                        "${summary.name} will be removed and any pending import will be cancelled."
                    },
                )
            },
            confirmButton = {
                Button(
                    enabled = !deleteWorking,
                    onClick = {
                        deleteWorking = true
                        scope.launch {
                            when (val result = runtime.deleteSource(summary.sourceId)) {
                                SourceMutationResult.Success -> {
                                    restoreDeleteActionFocus = false
                                    deleteConfirm = false
                                    deleteWorking = false
                                    onDeleted()
                                }
                                is SourceMutationResult.Failure -> {
                                    restoreDeleteActionFocus = true
                                    deleteConfirm = false
                                    deleteWorking = false
                                    error = tvPlaylistMutationFailureMessage(result.reason)
                                }
                            }
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleteWorking,
                    onClick = {
                        restoreDeleteActionFocus = true
                        deleteConfirm = false
                    },
                    modifier = Modifier.focusRequester(deleteCancelFocusRequester),
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TvPlaylistAddForm(
    mode: TvPlaylistAddMode,
    runtime: OwnPlayAppRuntime,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
) {
    val context = LocalContext.current
    val nameFocusRequester = remember { FocusRequester() }
    var name by remember(mode) { mutableStateOf("") }
    var endpoint by remember(mode) { mutableStateOf("") }
    var username by remember(mode) { mutableStateOf("") }
    var password by remember(mode) { mutableStateOf("") }
    var allowCleartext by remember(mode) { mutableStateOf(false) }
    var localUri by remember(mode) { mutableStateOf<String?>(null) }
    var error by remember(mode) { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val retained = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
            if (retained) {
                localUri = uri.toString()
                if (name.isBlank()) name = "Local playlist"
            } else {
                error = "OwnPlay could not retain access to this file."
            }
        }
    }

    LaunchedEffect(mode) {
        withFrameNanos { }
        nameFocusRequester.requestFocus()
    }

    TvPlaylistPageScaffold(
        title = "Add ${mode.title}",
        description = "Complete the fields, then choose Add. Text entry uses the Android TV keyboard.",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocusRequester),
            )

            when (mode) {
                TvPlaylistAddMode.XTREAM -> {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Checkbox(
                            checked = allowCleartext,
                            onCheckedChange = { allowCleartext = it },
                        )
                        Text("Allow HTTP for this provider")
                    }
                    if (allowCleartext) {
                        Text(
                            text = "HTTP does not encrypt Xtream credentials or stream traffic.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                TvPlaylistAddMode.REMOTE_M3U -> {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text("Playlist URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Checkbox(
                            checked = allowCleartext,
                            onCheckedChange = { allowCleartext = it },
                        )
                        Text("Allow HTTP for this playlist and EPG")
                    }
                    if (allowCleartext) {
                        Text(
                            text = "HTTP does not encrypt playlist, EPG, or stream traffic.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                TvPlaylistAddMode.LOCAL_M3U -> {
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (localUri == null) "Choose file" else "File selected")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Checkbox(
                            checked = allowCleartext,
                            onCheckedChange = { allowCleartext = it },
                        )
                        Text("Allow HTTP EPG links from this file")
                    }
                    if (allowCleartext) {
                        Text(
                            text = "HTTP EPG traffic is not encrypted.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Text(
                text = "After Add, channel and EPG import continue in the background.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = {
                    val validationError = validateTvPlaylistInput(
                        mode = mode,
                        name = name,
                        endpoint = endpoint,
                        username = username,
                        password = password,
                        allowCleartext = allowCleartext,
                        localUri = localUri,
                    )
                    if (validationError != null) {
                        error = validationError
                        return@Button
                    }

                    when (mode) {
                        TvPlaylistAddMode.XTREAM -> SourceSubmissionCoordinator.submitXtream(
                            runtime = runtime,
                            name = name,
                            serverUrl = endpoint,
                            username = username,
                            password = password,
                            allowCleartext = allowCleartext,
                        )
                        TvPlaylistAddMode.REMOTE_M3U -> SourceSubmissionCoordinator.submitRemoteM3u(
                            runtime = runtime,
                            name = name,
                            playlistUrl = endpoint,
                            allowCleartext = allowCleartext,
                        )
                        TvPlaylistAddMode.LOCAL_M3U -> SourceSubmissionCoordinator.submitLocalM3u(
                            runtime = runtime,
                            name = name,
                            documentUri = checkNotNull(localUri),
                            allowCleartext = allowCleartext,
                        )
                    }
                    onCompleted()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add")
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun TvPlaylistEditForm(
    runtime: OwnPlayAppRuntime,
    snapshot: SourceEditSnapshot,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val nameFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember(snapshot.sourceId) { FocusRequester() }
    val scope = rememberCoroutineScope()
    var name by remember(snapshot) { mutableStateOf(snapshot.name) }
    var endpoint by remember(snapshot) { mutableStateOf(snapshot.endpoint.orEmpty()) }
    var username by remember(snapshot) { mutableStateOf("") }
    var password by remember(snapshot) { mutableStateOf("") }
    var allowCleartext by remember(snapshot) { mutableStateOf(snapshot.allowCleartext) }
    var working by remember(snapshot) { mutableStateOf(false) }
    var restoreSaveFocus by remember(snapshot.sourceId) { mutableStateOf(false) }
    var error by remember(snapshot) { mutableStateOf<String?>(null) }

    BackHandler(enabled = working) { }

    LaunchedEffect(snapshot.sourceId) {
        withFrameNanos { }
        nameFocusRequester.requestFocus()
    }

    LaunchedEffect(snapshot.sourceId, working, restoreSaveFocus) {
        if (working || !restoreSaveFocus) return@LaunchedEffect
        withFrameNanos { }
        saveFocusRequester.requestFocus()
        restoreSaveFocus = false
    }

    TvPlaylistPageScaffold(
        title = "Edit ${snapshot.name}",
        description = "Edit supported source details. Saved provider credentials are never shown.",
        onBack = onBack,
        backEnabled = !working,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
                enabled = !working,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocusRequester),
            )

            if (snapshot.sourceKind == SourceKinds.XTREAM) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Server URL") },
                    singleLine = true,
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Leave both credential fields empty to keep the saved username and password.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("New username") },
                    singleLine = true,
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("New password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(
                        checked = allowCleartext,
                        onCheckedChange = { allowCleartext = it },
                        enabled = !working,
                    )
                    Text("Allow HTTP for this provider")
                }
            } else {
                Text(
                    text = "This source type currently supports renaming only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (working) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Text("Saving and refreshing…")
                }
            }
            Button(
                enabled = !working,
                onClick = {
                    restoreSaveFocus = true
                    working = true
                    error = null
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
                            SourceMutationResult.Success -> {
                                restoreSaveFocus = false
                                onSaved()
                            }
                            is SourceMutationResult.Failure -> {
                                working = false
                                error = tvPlaylistMutationFailureMessage(result.reason)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(saveFocusRequester),
            ) {
                Text("Save")
            }
            OutlinedButton(
                enabled = !working,
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun TvPlaylistPageScaffold(
    title: String,
    description: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextButton(
                onClick = onBack,
                enabled = backEnabled,
            ) {
                Text("‹ Back")
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        content()
    }
}

@Composable
private fun TvPlaylistNavigationRow(
    title: String,
    detail: String,
    focused: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val containerColor = if (focused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (focused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { state -> if (state.isFocused) onFocused() },
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvPlaylistActionRow(
    title: String,
    detail: String,
    focused: Boolean,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerLow
        focused -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        focused -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { state -> if (state.isFocused) onFocused() },
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun playlistSummaryStatus(
    summary: PlaylistSourceSummary,
    syncState: SourceSyncState?,
): String {
    val status = syncState?.let(::sourceSyncStatus)
    if (status != null) return status
    return if (summary.enabled) {
        "Ready • ${summary.channelCount} channels"
    } else {
        "Waiting for import or retry"
    }
}

private fun tvPlaylistSourceKindLabel(kind: String): String = when (kind) {
    SourceKinds.XTREAM -> "Xtream"
    SourceKinds.REMOTE_M3U -> "M3U URL"
    SourceKinds.LOCAL_M3U -> "Local M3U"
    else -> "Playlist"
}

private fun validateTvPlaylistInput(
    mode: TvPlaylistAddMode,
    name: String,
    endpoint: String,
    username: String,
    password: String,
    allowCleartext: Boolean,
    localUri: String?,
): String? {
    if (name.trim().isEmpty()) return "Enter a playlist name."

    return when (mode) {
        TvPlaylistAddMode.XTREAM -> when (val validation = SourceValidator.validateXtreamServer(endpoint)) {
            is UrlValidationResult.Invalid -> sourceErrorMessage(validation.error)
            is UrlValidationResult.Valid -> when {
                validation.usesCleartext && !allowCleartext ->
                    sourceErrorMessage(SourceError.CleartextTransportRequiresOptIn)
                username.trim().isEmpty() || password.isEmpty() ->
                    sourceErrorMessage(SourceError.InvalidCredentials)
                else -> null
            }
        }

        TvPlaylistAddMode.REMOTE_M3U ->
            when (val validation = SourceValidator.validateRemotePlaylistUrl(endpoint)) {
                is UrlValidationResult.Invalid -> sourceErrorMessage(validation.error)
                is UrlValidationResult.Valid -> if (validation.usesCleartext && !allowCleartext) {
                    sourceErrorMessage(SourceError.CleartextTransportRequiresOptIn)
                } else {
                    null
                }
            }

        TvPlaylistAddMode.LOCAL_M3U -> {
            val uri = localUri ?: return "Choose a local M3U file."
            SourceValidator.validateLocalDocumentUri(uri)?.let(::sourceErrorMessage)
        }
    }
}

private fun tvPlaylistMutationFailureMessage(failure: SourceMutationFailure): String = when (failure) {
    SourceMutationFailure.NotFound -> "Playlist no longer exists."
    SourceMutationFailure.InvalidName -> "Enter a playlist name."
    SourceMutationFailure.UnsupportedEdit -> "This playlist type only supports renaming for now."
    SourceMutationFailure.IncompleteCredentialReplacement ->
        "Enter both new username and password, or leave both empty."
    SourceMutationFailure.SecureStorageFailure -> "Secure storage failed."
    SourceMutationFailure.PersistenceFailure -> "Could not save playlist changes."
    is SourceMutationFailure.SourceFailure -> sourceErrorMessage(failure.error)
}
