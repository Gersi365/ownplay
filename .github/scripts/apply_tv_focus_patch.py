from pathlib import Path


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    Path(path).write_text(text, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one replacement target, found {count}: {old[:80]!r}")
    write(path, text.replace(old, new, 1))


def function_block(path: str, name: str) -> tuple[str, int, int, str]:
    text = read(path)
    token = f"fun {name}("
    token_index = text.find(token)
    if token_index < 0:
        raise RuntimeError(f"{path}: function {name} not found")
    start = text.rfind("@Composable", 0, token_index)
    if start < 0:
        raise RuntimeError(f"{path}: composable marker for {name} not found")
    end = text.find("\n@Composable", token_index + len(token))
    if end < 0:
        end = len(text)
    return text, start, end, text[start:end]


def replace_function(path: str, name: str, new_block: str) -> None:
    text, start, end, _ = function_block(path, name)
    write(path, text[:start] + new_block.rstrip() + "\n" + text[end:])


def transform_function(path: str, name: str, transform) -> None:
    text, start, end, block = function_block(path, name)
    updated = transform(block)
    if updated == block:
        raise RuntimeError(f"{path}: function {name} transform made no changes")
    write(path, text[:start] + updated + text[end:])


def add_modifier_parameter_and_root(path: str, name: str, *, make_root_playable: bool = False) -> None:
    def transform(block: str) -> str:
        signature_end = block.find("\n) {")
        if signature_end < 0:
            raise RuntimeError(f"{path}: signature end not found for {name}")
        if "\n    modifier: Modifier = Modifier," in block[:signature_end]:
            raise RuntimeError(f"{path}: {name} already has modifier parameter")
        block = block[:signature_end] + "\n    modifier: Modifier = Modifier," + block[signature_end:]
        root_marker = "modifier = Modifier"
        if root_marker not in block[signature_end:]:
            raise RuntimeError(f"{path}: root modifier not found for {name}")
        block = block[:signature_end] + block[signature_end:].replace(root_marker, "modifier = modifier", 1)
        if make_root_playable:
            old = """Surface(\n        modifier = modifier.fillMaxWidth(),"""
            new = """Surface(\n        modifier = modifier\n            .fillMaxWidth()\n            .clickable(onClick = onPlay),"""
            if old not in block:
                raise RuntimeError(f"{path}: playable root target missing for {name}")
            block = block.replace(old, new, 1)
        return block

    transform_function(path, name, transform)


# -----------------------------------------------------------------------------
# Shared pure TV focus policy + JVM tests.
# -----------------------------------------------------------------------------
policy_path = Path("app/src/main/java/app/ownplay/player/ui/OfflineMediaTvFocusPolicy.kt")
if policy_path.exists():
    raise RuntimeError(f"Unexpected existing file: {policy_path}")
policy_path.write_text(
    """package app.ownplay.player.ui\n\ninternal object OfflineMediaTvFocusPolicy {\n    fun preferredVisibleKey(\n        visibleKeys: List<String>,\n        rememberedKey: String?,\n    ): String? {\n        if (visibleKeys.isEmpty()) return null\n        return rememberedKey?.takeIf(visibleKeys::contains) ?: visibleKeys.first()\n    }\n}\n""",
    encoding="utf-8",
)

policy_test_path = Path("app/src/test/java/app/ownplay/player/ui/OfflineMediaTvFocusPolicyTest.kt")
if policy_test_path.exists():
    raise RuntimeError(f"Unexpected existing file: {policy_test_path}")
policy_test_path.write_text(
    """package app.ownplay.player.ui\n\nimport org.junit.Assert.assertEquals\nimport org.junit.Assert.assertNull\nimport org.junit.Test\n\nclass OfflineMediaTvFocusPolicyTest {\n    @Test\n    fun `remembered visible item wins`() {\n        assertEquals(\n            \"movie-b\",\n            OfflineMediaTvFocusPolicy.preferredVisibleKey(\n                visibleKeys = listOf(\"movie-a\", \"movie-b\"),\n                rememberedKey = \"movie-b\",\n            ),\n        )\n    }\n\n    @Test\n    fun `hidden remembered item falls back to first visible item`() {\n        assertEquals(\n            \"movie-a\",\n            OfflineMediaTvFocusPolicy.preferredVisibleKey(\n                visibleKeys = listOf(\"movie-a\", \"series-a\"),\n                rememberedKey = \"missing\",\n            ),\n        )\n    }\n\n    @Test\n    fun `empty media set has no focus target`() {\n        assertNull(\n            OfflineMediaTvFocusPolicy.preferredVisibleKey(\n                visibleKeys = emptyList(),\n                rememberedKey = \"old\",\n            ),\n        )\n    }\n}\n""",
    encoding="utf-8",
)

bridge_test_path = Path("app/src/test/java/app/ownplay/player/ui/DownloadPlaybackBridgeTest.kt")
if bridge_test_path.exists():
    raise RuntimeError(f"Unexpected existing file: {bridge_test_path}")
bridge_test_path.write_text(
    """package app.ownplay.player.ui\n\nimport org.junit.Assert.assertEquals\nimport org.junit.Test\n\nclass DownloadPlaybackBridgeTest {\n    @Test\n    fun `playback close returns focus only while owner is registered`() {\n        val owner = Any()\n        var restored: String? = null\n        try {\n            DownloadPlaybackBridge.registerFocusReturn(owner) { downloadId ->\n                restored = downloadId\n            }\n\n            DownloadPlaybackBridge.notifyPlaybackClosed(\"download-1\")\n            assertEquals(\"download-1\", restored)\n\n            DownloadPlaybackBridge.clearFocusReturn(owner)\n            DownloadPlaybackBridge.notifyPlaybackClosed(\"download-2\")\n            assertEquals(\"download-1\", restored)\n        } finally {\n            DownloadPlaybackBridge.clearFocusReturn(owner)\n        }\n    }\n}\n""",
    encoding="utf-8",
)

# -----------------------------------------------------------------------------
# Narrow Downloads playback bridge: add a return-focus signal only.
# -----------------------------------------------------------------------------
bridge_path = "app/src/main/java/app/ownplay/player/ui/DownloadPlaybackBridge.kt"
write(
    bridge_path,
    """package app.ownplay.player.ui\n\nimport app.ownplay.player.download.OfflineDownload\n\n/**\n * Narrow handoff from download-management UI to the activity-level offline playback host.\n * It does not own playback, persistence, navigation, or a player instance.\n */\ninternal object DownloadPlaybackBridge {\n    private var owner: Any? = null\n    private var action: ((OfflineDownload) -> Unit)? = null\n    private var focusReturnOwner: Any? = null\n    private var focusReturnAction: ((String) -> Unit)? = null\n\n    fun register(\n        owner: Any,\n        action: (OfflineDownload) -> Unit,\n    ) {\n        this.owner = owner\n        this.action = action\n    }\n\n    fun clear(owner: Any) {\n        if (this.owner === owner) {\n            this.owner = null\n            action = null\n        }\n    }\n\n    fun request(download: OfflineDownload): Boolean {\n        val current = action ?: return false\n        current(download)\n        return true\n    }\n\n    fun registerFocusReturn(\n        owner: Any,\n        action: (downloadId: String) -> Unit,\n    ) {\n        focusReturnOwner = owner\n        focusReturnAction = action\n    }\n\n    fun clearFocusReturn(owner: Any) {\n        if (focusReturnOwner === owner) {\n            focusReturnOwner = null\n            focusReturnAction = null\n        }\n    }\n\n    fun notifyPlaybackClosed(downloadId: String) {\n        focusReturnAction?.invoke(downloadId)\n    }\n}\n""",
)

# Activity overlay removal notifies the underlying Downloads UI after playback closes.
main_path = "app/src/main/java/app/ownplay/player/MainActivity.kt"
replace_once(
    main_path,
    """                                        onExit = {\n                                            downloadPlaybackSession = null\n                                        },""",
    """                                        onExit = {\n                                            downloadPlaybackSession = null\n                                            DownloadPlaybackBridge.notifyPlaybackClosed(\n                                                session.download.downloadId,\n                                            )\n                                        },""",
)

# -----------------------------------------------------------------------------
# Downloads: deterministic primary-action focus + return after overlay playback.
# -----------------------------------------------------------------------------
downloads_path = "app/src/main/java/app/ownplay/player/ui/DownloadsSettingsScreen.kt"
replace_once(
    downloads_path,
    "import androidx.compose.foundation.layout.Arrangement\n",
    "import androidx.compose.foundation.border\nimport androidx.compose.foundation.layout.Arrangement\n",
)
replace_once(
    downloads_path,
    "import androidx.compose.foundation.lazy.items\n",
    "import androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.rememberLazyListState\n",
)
replace_once(
    downloads_path,
    "import androidx.compose.runtime.getValue\n",
    "import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableIntStateOf\n",
)
replace_once(
    downloads_path,
    "import androidx.compose.runtime.setValue\n",
    "import androidx.compose.runtime.setValue\nimport androidx.compose.runtime.withFrameNanos\n",
)
replace_once(
    downloads_path,
    "import androidx.compose.ui.focus.focusRequester\n",
    "import androidx.compose.ui.focus.focusRequester\nimport androidx.compose.ui.focus.onFocusChanged\n",
)

replace_once(
    downloads_path,
    """    val downloads by runtime.observeAll().collectAsState(initial = emptyList())\n    val scope = rememberCoroutineScope()\n    var pendingRemoval by remember { mutableStateOf<OfflineDownload?>(null) }""",
    """    val downloads by runtime.observeAll().collectAsState(initial = emptyList())\n    val downloadIds = remember(downloads) { downloads.map { it.downloadId } }\n    val downloadListState = rememberLazyListState()\n    val downloadItemFocusRequester = remember { FocusRequester() }\n    val focusReturnOwner = remember { Any() }\n    val scope = rememberCoroutineScope()\n    var pendingRemoval by remember { mutableStateOf<OfflineDownload?>(null) }\n    var focusDownloadId by remember { mutableStateOf<String?>(null) }\n    var focusRequestGeneration by remember { mutableIntStateOf(0) }\n    var rememberedDownloadId by remember { mutableStateOf<String?>(null) }\n    var initialDownloadFocusRequested by remember { mutableStateOf(false) }""",
)

replace_once(
    downloads_path,
    """    LaunchedEffect(isTelevision, focusBackOnEntry) {\n        if (isTelevision && focusBackOnEntry && onBack != null) {\n            backFocusRequester.requestFocus()\n        }\n    }""",
    """    DisposableEffect(isTelevision, focusReturnOwner) {\n        if (isTelevision) {\n            DownloadPlaybackBridge.registerFocusReturn(focusReturnOwner) { downloadId ->\n                focusDownloadId = downloadId\n                focusRequestGeneration += 1\n            }\n        }\n        onDispose { DownloadPlaybackBridge.clearFocusReturn(focusReturnOwner) }\n    }\n\n    LaunchedEffect(isTelevision, focusBackOnEntry, downloadIds.firstOrNull()) {\n        if (!isTelevision || initialDownloadFocusRequested) return@LaunchedEffect\n        initialDownloadFocusRequested = true\n        if (focusBackOnEntry && onBack != null) {\n            withFrameNanos { }\n            backFocusRequester.requestFocus()\n            return@LaunchedEffect\n        }\n        OfflineMediaTvFocusPolicy.preferredVisibleKey(\n            visibleKeys = downloadIds,\n            rememberedKey = rememberedDownloadId,\n        )?.let { target ->\n            focusDownloadId = target\n            focusRequestGeneration += 1\n        }\n    }\n\n    LaunchedEffect(\n        isTelevision,\n        focusDownloadId,\n        focusRequestGeneration,\n        downloadIds,\n    ) {\n        if (!isTelevision || focusRequestGeneration <= 0) return@LaunchedEffect\n        val target = focusDownloadId ?: return@LaunchedEffect\n        val index = downloadIds.indexOf(target)\n        if (index < 0) {\n            OfflineMediaTvFocusPolicy.preferredVisibleKey(\n                visibleKeys = downloadIds,\n                rememberedKey = rememberedDownloadId,\n            )?.takeIf { it != target }?.let { fallback ->\n                focusDownloadId = fallback\n                focusRequestGeneration += 1\n            }\n            return@LaunchedEffect\n        }\n        downloadListState.scrollToItem(index)\n        withFrameNanos { }\n        downloadItemFocusRequester.requestFocus()\n        rememberedDownloadId = target\n    }""",
)

replace_once(
    downloads_path,
    """        LazyColumn(\n            modifier = Modifier""",
    """        LazyColumn(\n            state = downloadListState,\n            modifier = Modifier""",
)
replace_once(
    downloads_path,
    """                DownloadRow(\n                    download = download,""",
    """                DownloadRow(\n                    download = download,\n                    primaryActionFocusRequester = downloadItemFocusRequester\n                        .takeIf { focusDownloadId == download.downloadId },\n                    onFocusWithin = { rememberedDownloadId = download.downloadId },""",
)


def transform_download_row(block: str) -> str:
    block = block.replace(
        """private fun DownloadRow(\n    download: OfflineDownload,""",
        """private fun DownloadRow(\n    download: OfflineDownload,\n    primaryActionFocusRequester: FocusRequester?,\n    onFocusWithin: () -> Unit,""",
        1,
    )
    old = ") {\n    Surface(\n        modifier = Modifier.fillMaxWidth(),"
    new = ") {\n    var rowFocused by remember(download.downloadId) { mutableStateOf(false) }\n    val primaryActionModifier = primaryActionFocusRequester\n        ?.let { requester -> Modifier.focusRequester(requester) }\n        ?: Modifier\n    Surface(\n        modifier = Modifier\n            .fillMaxWidth()\n            .then(\n                if (rowFocused) {\n                    Modifier.border(\n                        width = 2.dp,\n                        color = MaterialTheme.colorScheme.primary,\n                        shape = RoundedCornerShape(14.dp),\n                    )\n                } else {\n                    Modifier\n                },\n            ),"
    if old not in block:
        raise RuntimeError("DownloadRow surface target missing")
    block = block.replace(old, new, 1)
    old = """            Row(\n                verticalAlignment = Alignment.CenterVertically,"""
    new = """            Row(\n                modifier = Modifier.onFocusChanged { focusState ->\n                    rowFocused = focusState.hasFocus\n                    if (focusState.hasFocus) onFocusWithin()\n                },\n                verticalAlignment = Alignment.CenterVertically,"""
    if old not in block:
        raise RuntimeError("DownloadRow row target missing")
    block = block.replace(old, new, 1)
    replacements = {
        "IconButton(onClick = onPause) {": "IconButton(onClick = onPause, modifier = primaryActionModifier) {",
        "IconButton(onClick = onResume) {": "IconButton(onClick = onResume, modifier = primaryActionModifier) {",
        "TextButton(onClick = onPlayOffline) {": "TextButton(onClick = onPlayOffline, modifier = primaryActionModifier) {",
        "IconButton(onClick = onRetry) {": "IconButton(onClick = onRetry, modifier = primaryActionModifier) {",
    }
    for old_button, new_button in replacements.items():
        if block.count(old_button) != 1:
            raise RuntimeError(f"DownloadRow button target mismatch: {old_button}")
        block = block.replace(old_button, new_button, 1)
    return block


transform_function(downloads_path, "DownloadRow", transform_download_row)

# -----------------------------------------------------------------------------
# Unified Library: semantic keys, preserved lazy state, focus ring, playback return.
# -----------------------------------------------------------------------------
library_path = "app/src/main/java/app/ownplay/player/ui/library/UnifiedLibraryRoute.kt"
replace_once(
    library_path,
    "package app.ownplay.player.ui.library\n\n",
    "package app.ownplay.player.ui.library\n\nimport android.content.res.Configuration\n",
)
replace_once(
    library_path,
    "import androidx.compose.foundation.clickable\n",
    "import androidx.compose.foundation.border\nimport androidx.compose.foundation.clickable\n",
)
replace_once(
    library_path,
    "import androidx.compose.foundation.lazy.LazyColumn\n",
    "import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.LazyListState\n",
)
replace_once(
    library_path,
    "import androidx.compose.foundation.lazy.grid.GridCells\n",
    "import androidx.compose.foundation.lazy.grid.GridCells\nimport androidx.compose.foundation.lazy.grid.LazyGridState\n",
)
replace_once(
    library_path,
    "import androidx.compose.foundation.lazy.grid.items as gridItems\n",
    "import androidx.compose.foundation.lazy.grid.items as gridItems\nimport androidx.compose.foundation.lazy.grid.rememberLazyGridState\n",
)
replace_once(
    library_path,
    "import androidx.compose.foundation.lazy.items as listItems\n",
    "import androidx.compose.foundation.lazy.items as listItems\nimport androidx.compose.foundation.lazy.rememberLazyListState\n",
)
replace_once(
    library_path,
    "import androidx.compose.runtime.getValue\n",
    "import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableIntStateOf\n",
)
replace_once(
    library_path,
    "import androidx.compose.runtime.setValue\n",
    "import androidx.compose.runtime.setValue\nimport androidx.compose.runtime.withFrameNanos\n",
)
replace_once(
    library_path,
    "import androidx.compose.ui.Alignment\n",
    "import androidx.compose.ui.Alignment\nimport androidx.compose.ui.focus.FocusRequester\nimport androidx.compose.ui.focus.focusRequester\nimport androidx.compose.ui.focus.onFocusChanged\n",
)
replace_once(
    library_path,
    "import androidx.compose.ui.platform.LocalContext\n",
    "import androidx.compose.ui.platform.LocalConfiguration\nimport androidx.compose.ui.platform.LocalContext\n",
)
replace_once(
    library_path,
    "import app.ownplay.player.ui.view.ContentViewMode\n",
    "import app.ownplay.player.ui.OfflineMediaTvFocusPolicy\nimport app.ownplay.player.ui.view.ContentViewMode\n",
)

replace_once(
    library_path,
    """    val context = LocalContext.current\n    val scope = rememberCoroutineScope()""",
    """    val context = LocalContext.current\n    val configuration = LocalConfiguration.current\n    val isTelevision =\n        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION\n    val scope = rememberCoroutineScope()""",
)
replace_once(
    library_path,
    """    val viewModeStore = remember(context) {\n        ContentViewModeStore(context.applicationContext)\n    }""",
    """    val viewModeStore = remember(context) {\n        ContentViewModeStore(context.applicationContext)\n    }\n    val libraryListState = rememberLazyListState()\n    val libraryGridState = rememberLazyGridState()\n    val libraryItemFocusRequester = remember { FocusRequester() }""",
)
replace_once(
    library_path,
    """    var playbackError by remember { mutableStateOf<String?>(null) }\n    var selectedSeriesKey by remember { mutableStateOf<LibrarySeriesKey?>(null) }""",
    """    var playbackError by remember { mutableStateOf<String?>(null) }\n    var selectedSeriesKey by remember { mutableStateOf<LibrarySeriesKey?>(null) }\n    var focusItemKey by remember(sourceId) { mutableStateOf<String?>(null) }\n    var focusRequestGeneration by remember(sourceId) { mutableIntStateOf(0) }\n    var rememberedFocusItemKey by remember(sourceId) { mutableStateOf<String?>(null) }\n    var initialLibraryItemFocusRequested by remember(sourceId) { mutableStateOf(false) }\n    var pendingMovieReturnFocusKey by remember(sourceId) { mutableStateOf<String?>(null) }\n    var seriesReturnEpisodeId by remember(sourceId) { mutableStateOf<String?>(null) }\n    var seriesReturnFocusGeneration by remember(sourceId) { mutableIntStateOf(0) }""",
)
replace_once(
    library_path,
    """            onExit = {\n                runtime.playbackController.stop()\n                playbackSession = null\n            },""",
    """            onExit = {\n                runtime.playbackController.stop()\n                playbackSession = null\n                val movieReturnKey = pendingMovieReturnFocusKey\n                pendingMovieReturnFocusKey = null\n                if (selectedSeriesKey != null && seriesReturnEpisodeId != null) {\n                    seriesReturnFocusGeneration += 1\n                } else if (movieReturnKey != null) {\n                    focusItemKey = movieReturnKey\n                    rememberedFocusItemKey = movieReturnKey\n                    focusRequestGeneration += 1\n                }\n            },""",
)
replace_once(
    library_path,
    """            playbackError = playbackError,\n            onBack = { selectedSeriesKey = null },""",
    """            playbackError = playbackError,\n            returnFocusEpisodeId = seriesReturnEpisodeId,\n            returnFocusGeneration = seriesReturnFocusGeneration,\n            onBack = {\n                selectedSeriesKey = null\n                seriesReturnEpisodeId = null\n                rememberedFocusItemKey?.let { target ->\n                    focusItemKey = target\n                    focusRequestGeneration += 1\n                }\n            },""",
)
replace_once(
    library_path,
    """            onPlay = ::playDownload,""",
    """            onPlay = { download ->\n                pendingMovieReturnFocusKey = null\n                seriesReturnEpisodeId = download.contentId\n                playDownload(download)\n            },""",
)

replace_once(
    library_path,
    """    val hasItems = movieCount + seriesCount > 0\n\n    Column(""",
    """    val hasItems = movieCount + seriesCount > 0\n    val visibleFocusKeys = remember(\n        filter,\n        sourceId,\n        visibleMovies,\n        orphanedOfflineMovies,\n        visibleSeries,\n        orphanedOfflineSeries,\n    ) {\n        libraryVisibleFocusKeys(\n            filter = filter,\n            sourceId = sourceId,\n            visibleMovies = visibleMovies,\n            orphanedOfflineMovies = orphanedOfflineMovies,\n            visibleSeries = visibleSeries,\n            orphanedOfflineSeries = orphanedOfflineSeries,\n        )\n    }\n\n    LaunchedEffect(isTelevision, visibleFocusKeys, libraryViewMode) {\n        if (!isTelevision || visibleFocusKeys.isEmpty()) return@LaunchedEffect\n        val currentTargetStillVisible = focusItemKey?.let(visibleFocusKeys::contains) == true\n        if (initialLibraryItemFocusRequested && currentTargetStillVisible) return@LaunchedEffect\n        val target = OfflineMediaTvFocusPolicy.preferredVisibleKey(\n            visibleKeys = visibleFocusKeys,\n            rememberedKey = rememberedFocusItemKey,\n        ) ?: return@LaunchedEffect\n        initialLibraryItemFocusRequested = true\n        focusItemKey = target\n        focusRequestGeneration += 1\n    }\n\n    Column(""",
)

replace_once(
    library_path,
    """            seriesGroupByIdentity = seriesGroupByIdentity,\n            onOpenMovie = { movieSourceId, movieId ->""",
    """            seriesGroupByIdentity = seriesGroupByIdentity,\n            focusKeys = visibleFocusKeys,\n            focusItemKey = focusItemKey,\n            focusRequestGeneration = focusRequestGeneration,\n            itemFocusRequester = libraryItemFocusRequester,\n            listState = libraryListState,\n            gridState = libraryGridState,\n            onItemFocused = { itemKey -> rememberedFocusItemKey = itemKey },\n            onOpenMovie = { movieSourceId, movieId ->""",
)
replace_once(
    library_path,
    """            onPlayOfflineMovie = ::playDownload,""",
    """            onPlayOfflineMovie = { download ->\n                seriesReturnEpisodeId = null\n                val catalogKey = libraryCatalogMovieFocusKey(\n                    sourceId = download.sourceId,\n                    movieId = download.contentId,\n                )\n                val offlineKey = libraryOfflineMovieFocusKey(download.downloadId)\n                pendingMovieReturnFocusKey = when {\n                    catalogKey in visibleFocusKeys -> catalogKey\n                    offlineKey in visibleFocusKeys -> offlineKey\n                    else -> rememberedFocusItemKey\n                }\n                playDownload(download)\n            },""",
)

new_catalog_view = r'''@Composable
private fun LibraryCatalogView(
    viewMode: ContentViewMode,
    filter: UnifiedLibraryFilter,
    sourceId: String?,
    offlineOnly: Boolean,
    visibleMovies: List<VodMovie>,
    orphanedOfflineMovies: List<OfflineDownload>,
    visibleSeries: List<SeriesSummary>,
    orphanedOfflineSeries: List<LibrarySeriesGroup>,
    movieDownloadsByKey: Map<String, OfflineDownload>,
    seriesGroupByIdentity: Map<String, LibrarySeriesGroup>,
    focusKeys: List<String>,
    focusItemKey: String?,
    focusRequestGeneration: Int,
    itemFocusRequester: FocusRequester,
    listState: LazyListState,
    gridState: LazyGridState,
    onItemFocused: (String) -> Unit,
    onOpenMovie: (sourceId: String, movieId: String) -> Unit,
    onOpenCatalogSeries: (sourceId: String, seriesId: String, group: LibrarySeriesGroup?) -> Unit,
    onOpenOfflineSeries: (LibrarySeriesGroup) -> Unit,
    onPlayOfflineMovie: (OfflineDownload) -> Unit,
    onPauseMovie: (OfflineDownload) -> Unit,
    onResumeMovie: (OfflineDownload) -> Unit,
    onRetryMovie: (OfflineDownload) -> Unit,
    onRemoveMovie: (OfflineDownload) -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentlyFocusedItemKey by remember { mutableStateOf<String?>(null) }
    val focusIndex = remember(focusKeys, focusItemKey) { focusKeys.indexOf(focusItemKey) }

    LaunchedEffect(
        viewMode,
        focusItemKey,
        focusRequestGeneration,
        focusIndex,
    ) {
        if (focusRequestGeneration <= 0 || focusIndex < 0) return@LaunchedEffect
        when (viewMode) {
            ContentViewMode.LIST -> listState.scrollToItem(focusIndex)
            ContentViewMode.COMPACT,
            ContentViewMode.CARDS,
            -> gridState.scrollToItem(focusIndex)
        }
        withFrameNanos { }
        itemFocusRequester.requestFocus()
    }

    fun itemModifier(itemKey: String): Modifier {
        val requesterModifier = if (itemKey == focusItemKey) {
            Modifier.focusRequester(itemFocusRequester)
        } else {
            Modifier
        }
        val ringModifier = if (currentlyFocusedItemKey == itemKey) {
            Modifier.border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp),
            )
        } else {
            Modifier
        }
        return requesterModifier
            .then(ringModifier)
            .onFocusChanged { focusState ->
                if (focusState.hasFocus) {
                    currentlyFocusedItemKey = itemKey
                    onItemFocused(itemKey)
                } else if (currentlyFocusedItemKey == itemKey) {
                    currentlyFocusedItemKey = null
                }
            }
    }

    when (viewMode) {
        ContentViewMode.CARDS -> LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (filter != UnifiedLibraryFilter.SERIES) {
                gridItems(visibleMovies, key = { "catalog-movie:${it.movieId}" }) { movie ->
                    val movieSourceId = sourceId ?: return@gridItems
                    UnifiedMovieCard(
                        movie = movie,
                        download = movieDownloadsByKey["$movieSourceId:${movie.movieId}"],
                        onOpen = { onOpenMovie(movieSourceId, movie.movieId) },
                        onPlayOffline = onPlayOfflineMovie,
                        onPause = onPauseMovie,
                        onResume = onResumeMovie,
                        onRetry = onRetryMovie,
                        onRemove = onRemoveMovie,
                        modifier = itemModifier(
                            libraryCatalogMovieFocusKey(movieSourceId, movie.movieId),
                        ),
                    )
                }
                gridItems(orphanedOfflineMovies, key = { "offline-movie:${it.downloadId}" }) { download ->
                    OfflineOnlyMovieCard(
                        download = download,
                        onPlay = { onPlayOfflineMovie(download) },
                        onRetry = { onRetryMovie(download) },
                        onRemove = { onRemoveMovie(download) },
                        modifier = itemModifier(libraryOfflineMovieFocusKey(download.downloadId)),
                    )
                }
            }

            if (filter != UnifiedLibraryFilter.MOVIES) {
                gridItems(visibleSeries, key = { "catalog-series:${it.seriesId}" }) { series ->
                    val seriesSourceId = sourceId ?: return@gridItems
                    val group = seriesGroupByIdentity["$seriesSourceId:${series.seriesId}"]
                    UnifiedSeriesCard(
                        series = series,
                        group = group,
                        offlineMode = offlineOnly,
                        onOpen = { onOpenCatalogSeries(seriesSourceId, series.seriesId, group) },
                        onOpenOfflineSeries = onOpenOfflineSeries,
                        modifier = itemModifier(
                            libraryCatalogSeriesFocusKey(seriesSourceId, series.seriesId),
                        ),
                    )
                }
                gridItems(orphanedOfflineSeries, key = { "offline-series:${it.key}" }) { group ->
                    LibrarySeriesCard(
                        group = group,
                        onOpenOfflineSeries = { onOpenOfflineSeries(group) },
                        modifier = itemModifier(libraryOfflineSeriesFocusKey(group)),
                    )
                }
            }
        }

        ContentViewMode.COMPACT -> LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 108.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (filter != UnifiedLibraryFilter.SERIES) {
                gridItems(visibleMovies, key = { "compact-movie:${it.movieId}" }) { movie ->
                    val movieSourceId = sourceId ?: return@gridItems
                    CompactMovieCard(
                        movie = movie,
                        download = movieDownloadsByKey["$movieSourceId:${movie.movieId}"],
                        onOpen = { onOpenMovie(movieSourceId, movie.movieId) },
                        onPlayOffline = onPlayOfflineMovie,
                        onPause = onPauseMovie,
                        onResume = onResumeMovie,
                        onRetry = onRetryMovie,
                        onRemove = onRemoveMovie,
                        modifier = itemModifier(
                            libraryCatalogMovieFocusKey(movieSourceId, movie.movieId),
                        ),
                    )
                }
                gridItems(orphanedOfflineMovies, key = { "compact-offline-movie:${it.downloadId}" }) { download ->
                    CompactOfflineMovieCard(
                        download = download,
                        onPlay = { onPlayOfflineMovie(download) },
                        onRemove = { onRemoveMovie(download) },
                        modifier = itemModifier(libraryOfflineMovieFocusKey(download.downloadId)),
                    )
                }
            }

            if (filter != UnifiedLibraryFilter.MOVIES) {
                gridItems(visibleSeries, key = { "compact-series:${it.seriesId}" }) { series ->
                    val seriesSourceId = sourceId ?: return@gridItems
                    val group = seriesGroupByIdentity["$seriesSourceId:${series.seriesId}"]
                    CompactSeriesCard(
                        series = series,
                        group = group,
                        onOpen = { onOpenCatalogSeries(seriesSourceId, series.seriesId, group) },
                        onOpenOfflineSeries = onOpenOfflineSeries,
                        modifier = itemModifier(
                            libraryCatalogSeriesFocusKey(seriesSourceId, series.seriesId),
                        ),
                    )
                }
                gridItems(orphanedOfflineSeries, key = { "compact-offline-series:${it.key}" }) { group ->
                    CompactOfflineSeriesCard(
                        group = group,
                        onOpen = { onOpenOfflineSeries(group) },
                        modifier = itemModifier(libraryOfflineSeriesFocusKey(group)),
                    )
                }
            }
        }

        ContentViewMode.LIST -> LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 14.dp),
        ) {
            if (filter != UnifiedLibraryFilter.SERIES) {
                listItems(visibleMovies, key = { "list-movie:${it.movieId}" }) { movie ->
                    val movieSourceId = sourceId ?: return@listItems
                    MovieListRow(
                        movie = movie,
                        download = movieDownloadsByKey["$movieSourceId:${movie.movieId}"],
                        onOpen = { onOpenMovie(movieSourceId, movie.movieId) },
                        onPlayOffline = onPlayOfflineMovie,
                        onPause = onPauseMovie,
                        onResume = onResumeMovie,
                        onRetry = onRetryMovie,
                        onRemove = onRemoveMovie,
                        modifier = itemModifier(
                            libraryCatalogMovieFocusKey(movieSourceId, movie.movieId),
                        ),
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 70.dp))
                }
                listItems(orphanedOfflineMovies, key = { "list-offline-movie:${it.downloadId}" }) { download ->
                    OfflineMovieListRow(
                        download = download,
                        onPlay = { onPlayOfflineMovie(download) },
                        onRemove = { onRemoveMovie(download) },
                        modifier = itemModifier(libraryOfflineMovieFocusKey(download.downloadId)),
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 70.dp))
                }
            }

            if (filter != UnifiedLibraryFilter.MOVIES) {
                listItems(visibleSeries, key = { "list-series:${it.seriesId}" }) { series ->
                    val seriesSourceId = sourceId ?: return@listItems
                    val group = seriesGroupByIdentity["$seriesSourceId:${series.seriesId}"]
                    SeriesListRow(
                        series = series,
                        group = group,
                        offlineMode = offlineOnly,
                        onOpen = { onOpenCatalogSeries(seriesSourceId, series.seriesId, group) },
                        onOpenOfflineSeries = onOpenOfflineSeries,
                        modifier = itemModifier(
                            libraryCatalogSeriesFocusKey(seriesSourceId, series.seriesId),
                        ),
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 70.dp))
                }
                listItems(orphanedOfflineSeries, key = { "list-offline-series:${it.key}" }) { group ->
                    OfflineSeriesListRow(
                        group = group,
                        onOpen = { onOpenOfflineSeries(group) },
                        modifier = itemModifier(libraryOfflineSeriesFocusKey(group)),
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 70.dp))
                }
            }
        }
    }
}
'''
replace_function(library_path, "LibraryCatalogView", new_catalog_view)

for component_name in (
    "UnifiedMovieCard",
    "UnifiedSeriesCard",
    "OfflineOnlyMovieCard",
    "CompactMovieCard",
    "CompactOfflineMovieCard",
    "CompactSeriesCard",
    "CompactOfflineSeriesCard",
    "MovieListRow",
    "OfflineMovieListRow",
    "SeriesListRow",
    "OfflineSeriesListRow",
):
    add_modifier_parameter_and_root(
        library_path,
        component_name,
        make_root_playable=(component_name == "OfflineOnlyMovieCard"),
    )

helper_marker = "\nprivate suspend fun enqueueSeriesEpisode("
helper_text = r'''
private fun libraryCatalogMovieFocusKey(sourceId: String, movieId: String): String =
    "movie:$sourceId:$movieId"

private fun libraryOfflineMovieFocusKey(downloadId: String): String =
    "offline-movie:$downloadId"

private fun libraryCatalogSeriesFocusKey(sourceId: String, seriesId: String): String =
    "series:$sourceId:$seriesId"

private fun libraryOfflineSeriesFocusKey(group: LibrarySeriesGroup): String =
    "offline-series:${group.key.sourceId}:${group.key.identity}"

private fun libraryVisibleFocusKeys(
    filter: UnifiedLibraryFilter,
    sourceId: String?,
    visibleMovies: List<VodMovie>,
    orphanedOfflineMovies: List<OfflineDownload>,
    visibleSeries: List<SeriesSummary>,
    orphanedOfflineSeries: List<LibrarySeriesGroup>,
): List<String> = buildList {
    val resolvedSourceId = sourceId ?: return@buildList
    if (filter != UnifiedLibraryFilter.SERIES) {
        visibleMovies.forEach { movie ->
            add(libraryCatalogMovieFocusKey(resolvedSourceId, movie.movieId))
        }
        orphanedOfflineMovies.forEach { download ->
            add(libraryOfflineMovieFocusKey(download.downloadId))
        }
    }
    if (filter != UnifiedLibraryFilter.MOVIES) {
        visibleSeries.forEach { series ->
            add(libraryCatalogSeriesFocusKey(resolvedSourceId, series.seriesId))
        }
        orphanedOfflineSeries.forEach { group ->
            add(libraryOfflineSeriesFocusKey(group))
        }
    }
}
'''
text = read(library_path)
if text.count(helper_marker) != 1:
    raise RuntimeError("UnifiedLibraryRoute helper insertion marker mismatch")
write(library_path, text.replace(helper_marker, "\n" + helper_text.rstrip() + helper_marker, 1))

# -----------------------------------------------------------------------------
# Library Series: card accepts focus modifier; episode playback returns to action.
# -----------------------------------------------------------------------------
series_path = "app/src/main/java/app/ownplay/player/ui/library/LibrarySeriesComponents.kt"
replace_once(
    series_path,
    "import androidx.compose.runtime.setValue\n",
    "import androidx.compose.runtime.setValue\nimport androidx.compose.runtime.withFrameNanos\n",
)
add_modifier_parameter_and_root(series_path, "LibrarySeriesCard")

replace_once(
    series_path,
    """    group: LibrarySeriesGroup,\n    playbackError: String?,\n    onBack: () -> Unit,""",
    """    group: LibrarySeriesGroup,\n    playbackError: String?,\n    returnFocusEpisodeId: String?,\n    returnFocusGeneration: Int,\n    onBack: () -> Unit,""",
)
replace_once(
    series_path,
    """    val seriesId = group.seriesId\n    val detailBackFocusRequester = remember(group.key) { FocusRequester() }""",
    """    val seriesId = group.seriesId\n    val detailBackFocusRequester = remember(group.key) { FocusRequester() }\n    val episodeActionFocusRequester = remember(group.key) { FocusRequester() }""",
)
replace_once(
    series_path,
    """    val selectedEpisode = visibleEpisodes.firstOrNull { it.episodeId == selectedEpisodeId }\n    val totalCatalogEpisodes = fullDetails?.seasons?.sumOf { it.episodes.size }""",
    """    val selectedEpisode = visibleEpisodes.firstOrNull { it.episodeId == selectedEpisodeId }\n    val totalCatalogEpisodes = fullDetails?.seasons?.sumOf { it.episodes.size }\n\n    LaunchedEffect(\n        isTelevision,\n        returnFocusEpisodeId,\n        returnFocusGeneration,\n        selectedEpisodeId,\n    ) {\n        if (\n            isTelevision &&\n            returnFocusGeneration > 0 &&\n            returnFocusEpisodeId != null &&\n            selectedEpisodeId == returnFocusEpisodeId\n        ) {\n            withFrameNanos { }\n            episodeActionFocusRequester.requestFocus()\n        }\n    }""",
)
replace_once(
    series_path,
    """            selectedEpisode != null -> OfflineEpisodeHero(\n                model = selectedEpisode,""",
    """            selectedEpisode != null -> OfflineEpisodeHero(\n                model = selectedEpisode,\n                primaryActionFocusRequester = episodeActionFocusRequester\n                    .takeIf {\n                        isTelevision &&\n                            returnFocusGeneration > 0 &&\n                            selectedEpisode.episodeId == returnFocusEpisodeId\n                    },""",
)


def transform_episode_hero(block: str) -> str:
    block = block.replace(
        """private fun OfflineEpisodeHero(\n    model: LibraryEpisodeCardModel,""",
        """private fun OfflineEpisodeHero(\n    model: LibraryEpisodeCardModel,\n    primaryActionFocusRequester: FocusRequester?,""",
        1,
    )
    old = ") {\n    val download = model.download"
    new = ") {\n    val download = model.download\n    val primaryActionModifier = primaryActionFocusRequester\n        ?.let { requester -> Modifier.focusRequester(requester) }\n        ?: Modifier"
    if old not in block:
        raise RuntimeError("OfflineEpisodeHero state target missing")
    block = block.replace(old, new, 1)
    button_replacements = {
        "Button(onClick = requireNotNull(onPlay)) {": "Button(onClick = requireNotNull(onPlay), modifier = primaryActionModifier) {",
        "FilledTonalButton(onClick = requireNotNull(onPause)) {": "FilledTonalButton(onClick = requireNotNull(onPause), modifier = primaryActionModifier) {",
        "FilledTonalButton(onClick = requireNotNull(onResume)) {": "FilledTonalButton(onClick = requireNotNull(onResume), modifier = primaryActionModifier) {",
        "FilledTonalButton(onClick = requireNotNull(onRetry)) {": "FilledTonalButton(onClick = requireNotNull(onRetry), modifier = primaryActionModifier) {",
        "FilledTonalButton(onClick = onDownload) {": "FilledTonalButton(onClick = onDownload, modifier = primaryActionModifier) {",
    }
    for old_button, new_button in button_replacements.items():
        if block.count(old_button) != 1:
            raise RuntimeError(f"OfflineEpisodeHero button target mismatch: {old_button}")
        block = block.replace(old_button, new_button, 1)
    return block


transform_function(series_path, "OfflineEpisodeHero", transform_episode_hero)

# Sanity assertions on the final patched sources.
checks = {
    bridge_path: ["registerFocusReturn", "notifyPlaybackClosed"],
    downloads_path: ["downloadListState.scrollToItem", "primaryActionFocusRequester", "rowFocused"],
    library_path: ["libraryVisibleFocusKeys", "focusRequestGeneration", "itemFocusRequester.requestFocus", "Modifier.border"],
    series_path: ["returnFocusEpisodeId", "episodeActionFocusRequester.requestFocus"],
    main_path: ["DownloadPlaybackBridge.notifyPlaybackClosed"],
}
for path, needles in checks.items():
    text = read(path)
    for needle in needles:
        if needle not in text:
            raise RuntimeError(f"{path}: missing patched marker {needle!r}")

print("TV focus hardening patch applied successfully")
