from pathlib import Path


def replace(path: str, old: str, new: str, count: int = 1) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    found = text.count(old)
    if found < count:
        raise SystemExit(f"{path}: expected {count} occurrence(s), found {found}: {old[:120]!r}")
    p.write_text(text.replace(old, new, count), encoding="utf-8")


# Configured playlists: use source-keyed progress and Retry for failed/pending imports.
settings = "app/src/main/java/app/ownplay/player/ui/PlaylistSettingsScreen.kt"
replace(
    settings,
    """    val activeSourceId =
        (activePlaylistSelection as? ActivePlaylistSelection.Ready)?.sourceId

    var addMode""",
    """    val activeSourceId =
        (activePlaylistSelection as? ActivePlaylistSelection.Ready)?.sourceId
    val sourceSyncStates by runtime.sourceSyncStates.collectAsState()

    var addMode""",
)
replace(
    settings,
    """    val pendingSourceName = if (
        syncState.stage == SourceSyncStage.LoadingChannels &&
        syncState.sourceId == null
    ) {
        syncState.sourceName?.trim().orEmpty()
    } else {
        ""
    }
    val showPendingSubmission =
        pendingSourceName.isNotEmpty() && summaries.none { summary ->
            !summary.enabled && summary.name == pendingSourceName
        }
    val configuredCount = summaries.size + if (showPendingSubmission) 1 else 0
""",
    """    val configuredCount = summaries.size
""",
)
replace(settings, "if (summaries.isEmpty() && !showPendingSubmission)", "if (summaries.isEmpty())")
replace(
    settings,
    """        if (showPendingSubmission) {
            PendingPlaylistCard(name = pendingSourceName)
        }

""",
    "",
)
replace(
    settings,
    """                syncState = syncState,
""",
    """                syncState = sourceSyncStates[summary.sourceId] ?: SourceSyncState(
                    sourceId = summary.sourceId,
                    sourceName = summary.name,
                ),
""",
)
replace(
    settings,
    """                        val saved = activePlaylistStore.set(summary.sourceId)
                        actionError = if (saved) null else "Could not save the active playlist."
""",
    """                        val saved = activePlaylistStore.set(summary.sourceId)
                        if (saved) {
                            runtime.onActiveSourceSelected(summary.sourceId)
                            actionError = null
                        } else {
                            actionError = "Could not save the active playlist."
                        }
""",
)
replace(
    settings,
    """                onRefresh = { scope.launch { runtime.refreshSource(summary.sourceId) } },
""",
    """                onRefresh = {
                    scope.launch {
                        if (summary.enabled) {
                            runtime.refreshSource(summary.sourceId)
                        } else {
                            runtime.retryPendingSource(summary.sourceId)
                        }
                    }
                },
""",
)
start = settings
p = Path(start)
text = p.read_text(encoding="utf-8")
block_start = text.index("@Composable\nprivate fun PendingPlaylistCard")
block_end = text.index("@Composable\nprivate fun PlaylistCard", block_start)
text = text[:block_start] + text[block_end:]
p.write_text(text, encoding="utf-8")
replace(
    settings,
    """    val importing = !summary.enabled
    val syncing = syncState.sourceId == summary.sourceId && syncState.stage.isLoading()
""",
    """    val importing = !summary.enabled
    val syncing = syncState.stage.isLoading()
    val importFailed = importing && syncState.stage == SourceSyncStage.ChannelsFailed
""",
)
replace(
    settings,
    """                        text = if (importing) {
                            "${sourceKindLabel(summary.sourceKind)} • Importing…"
                        } else {
                            "${sourceKindLabel(summary.sourceKind)} • ${summary.channelCount} channels"
                        },
""",
    """                        text = when {
                            importing && syncing -> "${sourceKindLabel(summary.sourceKind)} • Importing…"
                            importFailed -> "${sourceKindLabel(summary.sourceKind)} • Import failed"
                            importing -> "${sourceKindLabel(summary.sourceKind)} • Waiting to import…"
                            else -> "${sourceKindLabel(summary.sourceKind)} • ${summary.channelCount} channels"
                        },
""",
)
replace(settings, "if (importing || syncing) CircularProgressIndicator", "if (syncing) CircularProgressIndicator")
replace(
    settings,
    """                    text = when {
                        importing -> "Available after import"
                        isActive -> "Active playlist"
                        else -> "Use as active playlist"
                    },
""",
    """                    text = when {
                        importFailed -> "Retry import before activating"
                        importing -> "Available after import"
                        isActive -> "Active playlist"
                        else -> "Use as active playlist"
                    },
""",
)
replace(
    settings,
    """                TextButton(onClick = onRefresh, enabled = summary.enabled && !syncing) { Text("Refresh") }
""",
    """                TextButton(onClick = onRefresh, enabled = !syncing) {
                    Text(if (summary.enabled) "Refresh" else "Retry")
                }
""",
)


def patch_shell(path: str, mobile: bool) -> None:
    replace(path, "import androidx.compose.runtime.remember\n", "import androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberCoroutineScope\n")
    replace(path, "import androidx.compose.ui.platform.LocalConfiguration\n", "import androidx.compose.ui.platform.LocalConfiguration\nimport androidx.compose.ui.platform.LocalContext\n")
    replace(
        path,
        "import app.ownplay.player.source.SourceSyncState\n",
        """import app.ownplay.player.source.SourceSyncState
import app.ownplay.player.source.selection.ActivePlaylistSelection
import app.ownplay.player.source.selection.ActivePlaylistStore
import app.ownplay.player.source.selection.resolveActivePlaylistId
""",
    )
    replace(path, "import app.ownplay.player.ui.vod.VodRoute\n", "import app.ownplay.player.ui.vod.VodRoute\nimport kotlinx.coroutines.launch\n")

    if mobile:
        replace(
            path,
            """    val configuration = LocalConfiguration.current
    val summaries by runtime.observeSourceSummaries().collectAsState(initial = emptyList())
""",
            """    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val activePlaylistStore = remember(context) {
        ActivePlaylistStore(context.applicationContext)
    }
    val activePlaylistSelection by activePlaylistStore.observe().collectAsState(
        initial = ActivePlaylistSelection.Loading,
    )
    val activePlaylistScope = rememberCoroutineScope()
    val summaries by runtime.observeSourceSummaries().collectAsState(initial = emptyList())
""",
        )
    else:
        replace(
            path,
            """    val summaries by runtime.observeSourceSummaries().collectAsState(initial = emptyList())
""",
            """    val context = LocalContext.current
    val activePlaylistStore = remember(context) {
        ActivePlaylistStore(context.applicationContext)
    }
    val activePlaylistSelection by activePlaylistStore.observe().collectAsState(
        initial = ActivePlaylistSelection.Loading,
    )
    val activePlaylistScope = rememberCoroutineScope()
    val summaries by runtime.observeSourceSummaries().collectAsState(initial = emptyList())
""",
        )

    replace(
        path,
        """    val liveTransitionGate = remember { LivePlaybackTransitionGate() }

""",
        """    val liveTransitionGate = remember { LivePlaybackTransitionGate() }

    fun rememberActiveSource(sourceId: String?) {
        activeSourceId = sourceId
        activePlaylistScope.launch {
            activePlaylistStore.set(sourceId)
        }
    }

""",
    )
    replace(path, "activeSourceId = selection.request.sourceId", "rememberActiveSource(selection.request.sourceId)")

    p = Path(path)
    text = p.read_text(encoding="utf-8")
    old_start = text.index("    LaunchedEffect(summaries) {")
    old_end = text.index("\n    val previewActive", old_start)
    extra = "                fullscreenEntryReason = null\n" if mobile else ""
    new_effect = f"""    LaunchedEffect(summaries, activePlaylistSelection) {{
        val persistedSelection = activePlaylistSelection as? ActivePlaylistSelection.Ready
            ?: return@LaunchedEffect
        val enabledSourceIds = summaries
            .asSequence()
            .filter {{ summary -> summary.enabled }}
            .map {{ summary -> summary.sourceId }}
            .toList()
        val previousSourceId = activeSourceId
        val resolvedSourceId = resolveActivePlaylistId(
            persistedSourceId = persistedSelection.sourceId,
            currentSourceId = activeSourceId,
            enabledSourceIds = enabledSourceIds,
        )
        activeSourceId = resolvedSourceId

        if (enabledSourceIds.isNotEmpty() && persistedSelection.sourceId != resolvedSourceId) {{
            activePlaylistStore.set(resolvedSourceId)
        }}
        if (resolvedSourceId != null && previousSourceId != resolvedSourceId) {{
            runtime.onActiveSourceSelected(resolvedSourceId)
        }}

        val selectionSourceId = activeSelection?.request?.sourceId
        if (selectionSourceId != null && selectionSourceId != resolvedSourceId) {{
            stopLivePresentation {{
                activeSelection = null
                fullscreenSelection = null
{extra}            }}
        }}
        if (resolvedSourceId == null) {{
            requestedVodMovieId = null
            requestedSeriesId = null
            movieDetailReturnToLibrary = false
            seriesDetailReturnToLibrary = false
        }}
    }}
"""
    p.write_text(text[:old_start] + new_effect + text[old_end:], encoding="utf-8")

    replace(
        path,
        "val activeSummary = summaries.firstOrNull { it.sourceId == activeSourceId }",
        "val activeSummary = summaries.firstOrNull { it.sourceId == activeSourceId && it.enabled }",
    )
    # Library movie, library series, and Settings -> Live callbacks each assign sourceId directly.
    replace(path, "activeSourceId = sourceId", "rememberActiveSource(sourceId)", count=3)


patch_shell("app/src/mobile/java/app/ownplay/player/ui/MobileOwnPlayApp.kt", mobile=True)
patch_shell("app/src/tv/java/app/ownplay/player/ui/TVOwnPlayApp.kt", mobile=False)
