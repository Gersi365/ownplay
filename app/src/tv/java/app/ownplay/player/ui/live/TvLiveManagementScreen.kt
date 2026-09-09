package app.ownplay.player.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.live.LiveBrowseOrder
import app.ownplay.player.live.LiveBrowseSession
import app.ownplay.player.live.LiveBrowseState
import app.ownplay.player.live.LiveCategory
import app.ownplay.player.live.LiveChannelItem
import app.ownplay.player.live.LiveCustomGroup
import app.ownplay.player.persistence.PlaylistSourceSummary
import app.ownplay.player.personalization.CategoryOrderMutationResult
import app.ownplay.player.personalization.CategoryVisibilityMutationResult
import app.ownplay.player.personalization.ChannelBulkAction
import app.ownplay.player.personalization.ChannelEditReducer
import app.ownplay.player.personalization.ChannelEditState
import app.ownplay.player.personalization.CustomGroupMutationResult
import app.ownplay.player.personalization.ManualOrderMutationResult
import app.ownplay.player.personalization.ManualOrderPlacement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class TvLiveManagementPage {
    MAIN,
    CATEGORY_ORDER,
    CUSTOM_GROUPS,
    CHANNEL_CUSTOMIZATION,
}

private enum class TvLiveManagementRestoreTarget {
    CATEGORY_ORDER,
    CUSTOM_GROUPS,
    CHANNEL_CUSTOMIZATION,
}

private enum class TvChannelOrderAction {
    TOP,
    UP,
    DOWN,
    BOTTOM,
}

private data class PendingChannelOrderFocus(
    val channelId: String,
    val action: TvChannelOrderAction,
)

/** Dedicated remote-first Live Management presentation for the TV source set. */
@Composable
internal fun TvLiveManagementScreen(
    runtime: OwnPlayAppRuntime,
    summaries: List<PlaylistSourceSummary>,
    onBack: () -> Unit,
    focusFirstActionOnEntry: Boolean = false,
) {
    val sourceIds = summaries.map(PlaylistSourceSummary::sourceId)
    var sourceId by remember { mutableStateOf(summaries.firstOrNull()?.sourceId) }
    val selectedSourceId = sourceId?.takeIf { it in sourceIds } ?: sourceIds.firstOrNull()
    val firstActionFocusRequester = remember { FocusRequester() }
    val emptyBackFocusRequester = remember { FocusRequester() }

    LaunchedEffect(sourceIds, selectedSourceId) {
        if (sourceId != selectedSourceId) sourceId = selectedSourceId
    }

    if (selectedSourceId == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Live Management",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Add or enable a playlist before managing Live categories and channels.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TvManagementActionRow(
                label = "‹ Settings",
                onClick = onBack,
                focusRequester = emptyBackFocusRequester,
            )
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            emptyBackFocusRequester.requestFocus()
        }
        return
    }

    val scope = rememberCoroutineScope()
    val browseSession = remember(selectedSourceId) { LiveBrowseSession() }
    val browseFlow = remember(selectedSourceId) {
        browseSession.observe(runtime.observeLiveCatalog(selectedSourceId))
    }
    val state by browseFlow.collectAsState(initial = LiveBrowseState())
    var editState by remember(selectedSourceId) {
        mutableStateOf(ChannelEditState(isEditing = true))
    }
    var page by remember(selectedSourceId) { mutableStateOf(TvLiveManagementPage.MAIN) }
    var restoreTarget by remember(selectedSourceId) {
        mutableStateOf<TvLiveManagementRestoreTarget?>(null)
    }
    var categoryMutationInFlight by remember(selectedSourceId) { mutableStateOf(false) }
    var categoryError by remember(selectedSourceId) { mutableStateOf<String?>(null) }
    var orderError by remember(selectedSourceId) { mutableStateOf<String?>(null) }
    var pendingOrderFocus by remember(selectedSourceId) {
        mutableStateOf<PendingChannelOrderFocus?>(null)
    }

    val categoryOrderFocusRequester = remember { FocusRequester() }
    val categoryVisibilityFocusRequester = remember { FocusRequester() }
    val groupsFocusRequester = remember { FocusRequester() }
    val customizeFocusRequester = remember { FocusRequester() }
    val moveUpFocusRequester = remember { FocusRequester() }
    val moveDownFocusRequester = remember { FocusRequester() }

    val selectedCategory = state.query.categoryKey?.let { key ->
        state.categories.firstOrNull { category -> category.providerCategoryKey == key }
    }
    val selectedChannelId = editState.selectedChannelIds.singleOrNull()
    val selectedChannel = selectedChannelId?.let { channelId ->
        state.channels.firstOrNull { channel -> channel.channelId == channelId }
    }
    val selectedChannelIndex = selectedChannelId?.let { channelId ->
        state.channels.indexOfFirst { channel -> channel.channelId == channelId }
    } ?: -1
    val canMoveSelectedUp = selectedChannelIndex > 0
    val canMoveSelectedDown =
        selectedChannelIndex >= 0 && selectedChannelIndex < state.channels.lastIndex

    LaunchedEffect(selectedSourceId) {
        browseSession.setIncludeHidden(true)
        browseSession.setOrder(LiveBrowseOrder.MY_ORDER)
    }

    LaunchedEffect(state.channels) {
        editState = ChannelEditReducer.retainAvailable(
            state = editState,
            availableChannelIds = state.channels.map(LiveChannelItem::channelId),
        )
    }

    LaunchedEffect(focusFirstActionOnEntry, selectedSourceId) {
        if (focusFirstActionOnEntry) {
            withFrameNanos { }
            firstActionFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(page, restoreTarget) {
        if (page != TvLiveManagementPage.MAIN) return@LaunchedEffect
        val target = restoreTarget ?: return@LaunchedEffect
        withFrameNanos { }
        when (target) {
            TvLiveManagementRestoreTarget.CATEGORY_ORDER -> categoryOrderFocusRequester.requestFocus()
            TvLiveManagementRestoreTarget.CUSTOM_GROUPS -> groupsFocusRequester.requestFocus()
            TvLiveManagementRestoreTarget.CHANNEL_CUSTOMIZATION -> customizeFocusRequester.requestFocus()
        }
        restoreTarget = null
    }

    LaunchedEffect(
        selectedChannelId,
        canMoveSelectedUp,
        canMoveSelectedDown,
        pendingOrderFocus,
    ) {
        val request = pendingOrderFocus ?: return@LaunchedEffect
        if (selectedChannelId != request.channelId) {
            pendingOrderFocus = null
            return@LaunchedEffect
        }
        withFrameNanos { }
        when (request.action) {
            TvChannelOrderAction.TOP -> if (canMoveSelectedDown) {
                moveDownFocusRequester.requestFocus()
            }
            TvChannelOrderAction.BOTTOM -> if (canMoveSelectedUp) {
                moveUpFocusRequester.requestFocus()
            }
            TvChannelOrderAction.UP -> if (canMoveSelectedUp) {
                moveUpFocusRequester.requestFocus()
            } else if (canMoveSelectedDown) {
                moveDownFocusRequester.requestFocus()
            }
            TvChannelOrderAction.DOWN -> if (canMoveSelectedDown) {
                moveDownFocusRequester.requestFocus()
            } else if (canMoveSelectedUp) {
                moveUpFocusRequester.requestFocus()
            }
        }
        pendingOrderFocus = null
    }

    fun executeBulkAction(action: ChannelBulkAction) {
        val selection = editState.selectedChannelIds
        if (selection.isEmpty()) return
        scope.launch {
            runtime.executeChannelBulkAction(
                sourceId = selectedSourceId,
                selectedChannelIds = selection,
                action = action,
            )
        }
    }

    fun moveSelectedRelative(
        anchorChannelId: String,
        placement: ManualOrderPlacement,
        action: TvChannelOrderAction,
    ) {
        val channelId = selectedChannelId ?: return
        orderError = null
        scope.launch {
            try {
                when (
                    runtime.moveChannelRelative(
                        sourceId = selectedSourceId,
                        channelId = channelId,
                        anchorChannelId = anchorChannelId,
                        placement = placement,
                    )
                ) {
                    is ManualOrderMutationResult.Success -> {
                        pendingOrderFocus = PendingChannelOrderFocus(channelId, action)
                    }
                    is ManualOrderMutationResult.Rejected,
                    ManualOrderMutationResult.InvalidSourceId,
                    ManualOrderMutationResult.PersistenceFailure,
                    -> orderError = "Could not save channel order."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                orderError = "Could not save channel order."
            }
        }
    }

    fun moveSelectedTop() {
        if (!canMoveSelectedUp) return
        moveSelectedRelative(
            anchorChannelId = state.channels.first().channelId,
            placement = ManualOrderPlacement.BEFORE,
            action = TvChannelOrderAction.TOP,
        )
    }

    fun moveSelectedUp() {
        if (!canMoveSelectedUp) return
        moveSelectedRelative(
            anchorChannelId = state.channels[selectedChannelIndex - 1].channelId,
            placement = ManualOrderPlacement.BEFORE,
            action = TvChannelOrderAction.UP,
        )
    }

    fun moveSelectedDown() {
        if (!canMoveSelectedDown) return
        moveSelectedRelative(
            anchorChannelId = state.channels[selectedChannelIndex + 1].channelId,
            placement = ManualOrderPlacement.AFTER,
            action = TvChannelOrderAction.DOWN,
        )
    }

    fun moveSelectedBottom() {
        if (!canMoveSelectedDown) return
        moveSelectedRelative(
            anchorChannelId = state.channels.last().channelId,
            placement = ManualOrderPlacement.AFTER,
            action = TvChannelOrderAction.BOTTOM,
        )
    }

    fun toggleCategoryVisibility() {
        val category = selectedCategory ?: return
        if (categoryMutationInFlight) return
        categoryMutationInFlight = true
        categoryError = null
        scope.launch {
            try {
                val result = if (category.isHidden) {
                    runtime.unhideCategory(selectedSourceId, category.providerCategoryKey)
                } else {
                    runtime.hideCategory(selectedSourceId, category.providerCategoryKey)
                }
                if (result is CategoryVisibilityMutationResult.Failure) {
                    categoryError = "Could not save category visibility."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                categoryError = "Could not save category visibility."
            } finally {
                categoryMutationInFlight = false
                withFrameNanos { }
                categoryVisibilityFocusRequester.requestFocus()
            }
        }
    }

    fun returnToMain(target: TvLiveManagementRestoreTarget) {
        restoreTarget = target
        page = TvLiveManagementPage.MAIN
    }

    BackHandler(enabled = page != TvLiveManagementPage.MAIN) {
        when (page) {
            TvLiveManagementPage.CATEGORY_ORDER ->
                returnToMain(TvLiveManagementRestoreTarget.CATEGORY_ORDER)
            TvLiveManagementPage.CUSTOM_GROUPS ->
                returnToMain(TvLiveManagementRestoreTarget.CUSTOM_GROUPS)
            TvLiveManagementPage.CHANNEL_CUSTOMIZATION ->
                returnToMain(TvLiveManagementRestoreTarget.CHANNEL_CUSTOMIZATION)
            TvLiveManagementPage.MAIN -> Unit
        }
    }

    when (page) {
        TvLiveManagementPage.CATEGORY_ORDER -> TvCategoryOrderPage(
            categories = state.categories,
            error = orderError,
            onOrderChanged = { orderedKeys ->
                orderError = null
                scope.launch {
                    try {
                        when (
                            runtime.setCategoryOrder(
                                sourceId = selectedSourceId,
                                orderedCategoryKeys = orderedKeys,
                            )
                        ) {
                            is CategoryOrderMutationResult.Success -> orderError = null
                            is CategoryOrderMutationResult.Failure -> {
                                orderError = "Could not save category order."
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        orderError = "Could not save category order."
                    }
                }
            },
            onDone = { returnToMain(TvLiveManagementRestoreTarget.CATEGORY_ORDER) },
        )

        TvLiveManagementPage.CUSTOM_GROUPS -> TvCustomGroupsPage(
            groups = state.customGroups,
            selectedChannelIds = editState.selectedChannelIds,
            onCreateGroup = { name -> scope.launch { runtime.createCustomGroup(name) } },
            onRenameGroup = { groupId, name ->
                scope.launch { runtime.renameCustomGroup(groupId, name) }
            },
            onDeleteGroup = { groupId ->
                val clearDeletedActiveFilter = state.query.customGroupId == groupId
                scope.launch {
                    if (runtime.deleteCustomGroup(groupId) is CustomGroupMutationResult.Success &&
                        clearDeletedActiveFilter
                    ) {
                        browseSession.selectCustomGroup(null)
                    }
                }
            },
            onAddSelectionToGroup = { groupId ->
                executeBulkAction(ChannelBulkAction.AddToGroup(groupId))
            },
            onRemoveSelectionFromGroup = { groupId ->
                executeBulkAction(ChannelBulkAction.RemoveFromGroup(groupId))
            },
            onDone = { returnToMain(TvLiveManagementRestoreTarget.CUSTOM_GROUPS) },
        )

        TvLiveManagementPage.CHANNEL_CUSTOMIZATION -> {
            val channel = selectedChannel
            if (channel == null) {
                LaunchedEffect(Unit) {
                    returnToMain(TvLiveManagementRestoreTarget.CHANNEL_CUSTOMIZATION)
                }
            } else {
                TvChannelCustomizationPage(
                    channel = channel,
                    onSetLocalDisplayName = { name ->
                        scope.launch {
                            runtime.setLocalDisplayName(selectedSourceId, channel.channelId, name)
                        }
                    },
                    onClearLocalDisplayName = {
                        scope.launch {
                            runtime.clearLocalDisplayName(selectedSourceId, channel.channelId)
                        }
                    },
                    onSetLogoOverride = { logo ->
                        scope.launch {
                            runtime.setLogoOverride(selectedSourceId, channel.channelId, logo)
                        }
                    },
                    onClearLogoOverride = {
                        scope.launch {
                            runtime.clearLogoOverride(selectedSourceId, channel.channelId)
                        }
                    },
                    onDone = {
                        returnToMain(TvLiveManagementRestoreTarget.CHANNEL_CUSTOMIZATION)
                    },
                )
            }
        }

        TvLiveManagementPage.MAIN -> TvLiveManagementMainPage(
            summaries = summaries,
            selectedSourceId = selectedSourceId,
            state = state,
            editState = editState,
            selectedCategory = selectedCategory,
            selectedChannel = selectedChannel,
            canMoveSelectedUp = canMoveSelectedUp,
            canMoveSelectedDown = canMoveSelectedDown,
            categoryMutationInFlight = categoryMutationInFlight,
            categoryError = categoryError,
            orderError = orderError,
            firstActionFocusRequester = firstActionFocusRequester,
            categoryOrderFocusRequester = categoryOrderFocusRequester,
            categoryVisibilityFocusRequester = categoryVisibilityFocusRequester,
            groupsFocusRequester = groupsFocusRequester,
            customizeFocusRequester = customizeFocusRequester,
            moveUpFocusRequester = moveUpFocusRequester,
            moveDownFocusRequester = moveDownFocusRequester,
            onBack = onBack,
            onSourceSelected = { sourceId = it },
            onCategorySelected = {
                categoryError = null
                browseSession.selectCategory(it)
            },
            onChannelSelectionToggle = { channelId ->
                editState = ChannelEditReducer.toggleSelection(editState, channelId)
            },
            onSelectVisible = {
                editState = ChannelEditReducer.selectVisible(
                    state = editState,
                    visibleChannelIds = state.channels.map(LiveChannelItem::channelId),
                )
            },
            onClearSelection = {
                editState = ChannelEditReducer.clearSelection(editState)
            },
            onToggleCategoryVisibility = ::toggleCategoryVisibility,
            onOpenCategoryOrder = { page = TvLiveManagementPage.CATEGORY_ORDER },
            onOpenGroups = { page = TvLiveManagementPage.CUSTOM_GROUPS },
            onOpenCustomization = { page = TvLiveManagementPage.CHANNEL_CUSTOMIZATION },
            onHideSelected = { executeBulkAction(ChannelBulkAction.Hide) },
            onUnhideSelected = { executeBulkAction(ChannelBulkAction.Unhide) },
            onFavoriteSelected = { executeBulkAction(ChannelBulkAction.Favorite) },
            onUnfavoriteSelected = { executeBulkAction(ChannelBulkAction.RemoveFavorite) },
            onMoveTop = ::moveSelectedTop,
            onMoveUp = ::moveSelectedUp,
            onMoveDown = ::moveSelectedDown,
            onMoveBottom = ::moveSelectedBottom,
        )
    }
}

@Composable
private fun TvLiveManagementMainPage(
    summaries: List<PlaylistSourceSummary>,
    selectedSourceId: String,
    state: LiveBrowseState,
    editState: ChannelEditState,
    selectedCategory: LiveCategory?,
    selectedChannel: LiveChannelItem?,
    canMoveSelectedUp: Boolean,
    canMoveSelectedDown: Boolean,
    categoryMutationInFlight: Boolean,
    categoryError: String?,
    orderError: String?,
    firstActionFocusRequester: FocusRequester,
    categoryOrderFocusRequester: FocusRequester,
    categoryVisibilityFocusRequester: FocusRequester,
    groupsFocusRequester: FocusRequester,
    customizeFocusRequester: FocusRequester,
    moveUpFocusRequester: FocusRequester,
    moveDownFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onSourceSelected: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onChannelSelectionToggle: (String) -> Unit,
    onSelectVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onToggleCategoryVisibility: () -> Unit,
    onOpenCategoryOrder: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenCustomization: () -> Unit,
    onHideSelected: () -> Unit,
    onUnhideSelected: () -> Unit,
    onFavoriteSelected: () -> Unit,
    onUnfavoriteSelected: () -> Unit,
    onMoveTop: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveBottom: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "Live Management",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Sources and categories on the left, channels in the center, actions on the right.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TvManagementActionRow(
                label = "‹ Settings",
                onClick = onBack,
                compact = true,
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TvSourceCategoryPane(
                summaries = summaries,
                selectedSourceId = selectedSourceId,
                categories = state.categories,
                selectedCategoryKey = state.query.categoryKey,
                firstActionFocusRequester = firstActionFocusRequester,
                onSourceSelected = onSourceSelected,
                onCategorySelected = onCategorySelected,
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight(),
            )
            TvChannelSelectionPane(
                channels = state.channels,
                selectedChannelIds = editState.selectedChannelIds,
                onChannelSelectionToggle = onChannelSelectionToggle,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            TvManagementActionsPane(
                selectedCount = editState.selectedChannelIds.size,
                selectedCategory = selectedCategory,
                selectedChannel = selectedChannel,
                categoriesCount = state.categories.size,
                canMoveSelectedUp = canMoveSelectedUp,
                canMoveSelectedDown = canMoveSelectedDown,
                categoryMutationInFlight = categoryMutationInFlight,
                categoryError = categoryError,
                orderError = orderError,
                categoryOrderFocusRequester = categoryOrderFocusRequester,
                categoryVisibilityFocusRequester = categoryVisibilityFocusRequester,
                groupsFocusRequester = groupsFocusRequester,
                customizeFocusRequester = customizeFocusRequester,
                moveUpFocusRequester = moveUpFocusRequester,
                moveDownFocusRequester = moveDownFocusRequester,
                onSelectVisible = onSelectVisible,
                onClearSelection = onClearSelection,
                onToggleCategoryVisibility = onToggleCategoryVisibility,
                onOpenCategoryOrder = onOpenCategoryOrder,
                onOpenGroups = onOpenGroups,
                onOpenCustomization = onOpenCustomization,
                onHideSelected = onHideSelected,
                onUnhideSelected = onUnhideSelected,
                onFavoriteSelected = onFavoriteSelected,
                onUnfavoriteSelected = onUnfavoriteSelected,
                onMoveTop = onMoveTop,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onMoveBottom = onMoveBottom,
                modifier = Modifier
                    .width(318.dp)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun TvSourceCategoryPane(
    summaries: List<PlaylistSourceSummary>,
    selectedSourceId: String,
    categories: List<LiveCategory>,
    selectedCategoryKey: String?,
    firstActionFocusRequester: FocusRequester,
    onSourceSelected: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Sources", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            summaries.forEach { summary ->
                TvManagementActionRow(
                    label = summary.name,
                    selected = summary.sourceId == selectedSourceId,
                    onClick = { onSourceSelected(summary.sourceId) },
                    focusRequester = if (summary.sourceId == selectedSourceId) {
                        firstActionFocusRequester
                    } else {
                        null
                    },
                )
            }
            Text(
                text = "Categories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "all-categories") {
                    TvManagementActionRow(
                        label = "All channels",
                        selected = selectedCategoryKey == null,
                        onClick = { onCategorySelected(null) },
                    )
                }
                items(items = categories, key = LiveCategory::providerCategoryKey) { category ->
                    TvManagementActionRow(
                        label = category.name,
                        detail = if (category.isHidden) "Hidden" else null,
                        selected = category.providerCategoryKey == selectedCategoryKey,
                        onClick = { onCategorySelected(category.providerCategoryKey) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvChannelSelectionPane(
    channels: List<LiveChannelItem>,
    selectedChannelIds: Set<String>,
    onChannelSelectionToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (selectedChannelIds.isEmpty()) {
                    "Channels · ${channels.size}"
                } else {
                    "${selectedChannelIds.size} selected"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items = channels, key = LiveChannelItem::channelId) { channel ->
                    TvManagementChannelRow(
                        channel = channel,
                        selected = channel.channelId in selectedChannelIds,
                        onClick = { onChannelSelectionToggle(channel.channelId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvManagementChannelRow(
    channel: LiveChannelItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember(channel.channelId) { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(12.dp),
        color = when {
            focused -> MaterialTheme.colorScheme.primaryContainer
            selected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.50f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        },
        contentColor = if (focused) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = channel.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    if (selected) append("Selected")
                    if (channel.isHidden) {
                        if (isNotEmpty()) append(" · ")
                        append("Hidden")
                    }
                    if (channel.isFavorite) {
                        if (isNotEmpty()) append(" · ")
                        append("Favorite")
                    }
                    if (isEmpty()) append(channel.categoryName.orEmpty())
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvManagementActionsPane(
    selectedCount: Int,
    selectedCategory: LiveCategory?,
    selectedChannel: LiveChannelItem?,
    categoriesCount: Int,
    canMoveSelectedUp: Boolean,
    canMoveSelectedDown: Boolean,
    categoryMutationInFlight: Boolean,
    categoryError: String?,
    orderError: String?,
    categoryOrderFocusRequester: FocusRequester,
    categoryVisibilityFocusRequester: FocusRequester,
    groupsFocusRequester: FocusRequester,
    customizeFocusRequester: FocusRequester,
    moveUpFocusRequester: FocusRequester,
    moveDownFocusRequester: FocusRequester,
    onSelectVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onToggleCategoryVisibility: () -> Unit,
    onOpenCategoryOrder: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenCustomization: () -> Unit,
    onHideSelected: () -> Unit,
    onUnhideSelected: () -> Unit,
    onFavoriteSelected: () -> Unit,
    onUnfavoriteSelected: () -> Unit,
    onMoveTop: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveBottom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        LazyColumn(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item {
                Text(
                    text = "Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            item {
                TvManagementActionRow(
                    label = "Reorder categories",
                    enabled = categoriesCount > 1,
                    onClick = onOpenCategoryOrder,
                    focusRequester = categoryOrderFocusRequester,
                )
            }
            if (selectedCategory != null) {
                item {
                    TvManagementActionRow(
                        label = if (selectedCategory.isHidden) "Unhide category" else "Hide category",
                        enabled = !categoryMutationInFlight,
                        onClick = onToggleCategoryVisibility,
                        focusRequester = categoryVisibilityFocusRequester,
                    )
                }
            }
            item {
                TvManagementActionRow(
                    label = "Custom groups",
                    detail = if (selectedCount > 0) "$selectedCount selected" else null,
                    onClick = onOpenGroups,
                    focusRequester = groupsFocusRequester,
                )
            }
            item { TvManagementActionRow("Select visible channels", enabled = selectedCount == 0, onClick = onSelectVisible) }
            item { TvManagementActionRow("Clear selection", enabled = selectedCount > 0, onClick = onClearSelection) }
            item { TvManagementActionRow("Hide selected", enabled = selectedCount > 0, onClick = onHideSelected) }
            item { TvManagementActionRow("Unhide selected", enabled = selectedCount > 0, onClick = onUnhideSelected) }
            item { TvManagementActionRow("Favorite selected", enabled = selectedCount > 0, onClick = onFavoriteSelected) }
            item { TvManagementActionRow("Remove favorite", enabled = selectedCount > 0, onClick = onUnfavoriteSelected) }
            if (selectedChannel != null) {
                item {
                    TvManagementActionRow(
                        label = "Customize channel",
                        detail = selectedChannel.displayName,
                        onClick = onOpenCustomization,
                        focusRequester = customizeFocusRequester,
                    )
                }
                item { TvManagementActionRow("Move to top", enabled = canMoveSelectedUp, onClick = onMoveTop) }
                item {
                    TvManagementActionRow(
                        label = "Move up",
                        enabled = canMoveSelectedUp,
                        onClick = onMoveUp,
                        focusRequester = moveUpFocusRequester,
                    )
                }
                item {
                    TvManagementActionRow(
                        label = "Move down",
                        enabled = canMoveSelectedDown,
                        onClick = onMoveDown,
                        focusRequester = moveDownFocusRequester,
                    )
                }
                item { TvManagementActionRow("Move to bottom", enabled = canMoveSelectedDown, onClick = onMoveBottom) }
            }
            categoryError?.let { error -> item { TvManagementError(error) } }
            orderError?.let { error -> item { TvManagementError(error) } }
        }
    }
}

@Composable
private fun TvCategoryOrderPage(
    categories: List<LiveCategory>,
    error: String?,
    onOrderChanged: (List<String>) -> Unit,
    onDone: () -> Unit,
) {
    var working by remember(categories.map(LiveCategory::providerCategoryKey)) {
        mutableStateOf(categories)
    }
    val firstMoveFocusRequester = remember { FocusRequester() }
    val doneFocusRequester = remember { FocusRequester() }

    LaunchedEffect(working.size) {
        withFrameNanos { }
        if (working.size > 1) firstMoveFocusRequester.requestFocus() else doneFocusRequester.requestFocus()
    }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (index !in working.indices || target !in working.indices) return
        val next = working.toMutableList()
        val moved = next.removeAt(index)
        next.add(target, moved)
        working = next
        onOrderChanged(next.map(LiveCategory::providerCategoryKey))
    }

    TvManagementSubpage(
        title = "Reorder categories",
        description = "Use explicit Move up / Move down actions. Provider categories are not deleted.",
        onDone = onDone,
        doneFocusRequester = doneFocusRequester,
    ) {
        error?.let { TvManagementError(it) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                count = working.size,
                key = { index -> working[index].providerCategoryKey },
            ) { index ->
                val category = working[index]
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = category.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TvManagementActionRow(
                            label = "Move up",
                            enabled = index > 0,
                            compact = true,
                            onClick = { move(index, -1) },
                        )
                        TvManagementActionRow(
                            label = "Move down",
                            enabled = index < working.lastIndex,
                            compact = true,
                            focusRequester = if (index == 0 && working.size > 1) firstMoveFocusRequester else null,
                            onClick = { move(index, 1) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvCustomGroupsPage(
    groups: List<LiveCustomGroup>,
    selectedChannelIds: Set<String>,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onAddSelectionToGroup: (String) -> Unit,
    onRemoveSelectionFromGroup: (String) -> Unit,
    onDone: () -> Unit,
) {
    var newGroupName by remember { mutableStateOf("") }
    var renameGroupId by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deleteGroupId by remember { mutableStateOf<String?>(null) }
    val newGroupFocusRequester = remember { FocusRequester() }
    val doneFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        newGroupFocusRequester.requestFocus()
    }

    TvManagementSubpage(
        title = "Custom groups",
        description = "Create local groups and manage membership for selected channels.",
        onDone = onDone,
        doneFocusRequester = doneFocusRequester,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvManagementTextInput(
                value = newGroupName,
                onValueChange = { newGroupName = it },
                placeholder = "New group name",
                focusRequester = newGroupFocusRequester,
                modifier = Modifier.weight(1f),
            )
            TvManagementActionRow(
                label = "Create group",
                enabled = newGroupName.isNotBlank(),
                compact = true,
                onClick = {
                    val normalized = newGroupName.trim()
                    if (normalized.isNotEmpty()) {
                        onCreateGroup(normalized)
                        newGroupName = ""
                    }
                },
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (groups.isEmpty()) {
                item {
                    Text(
                        text = "No custom groups yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items = groups, key = LiveCustomGroup::groupId) { group ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (renameGroupId == group.groupId) {
                            TvManagementTextInput(
                                value = renameValue,
                                onValueChange = { renameValue = it },
                                placeholder = "Group name",
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            when {
                                renameGroupId == group.groupId -> {
                                    TvManagementActionRow(
                                        label = "Save name",
                                        enabled = renameValue.isNotBlank(),
                                        compact = true,
                                        onClick = {
                                            val normalized = renameValue.trim()
                                            if (normalized.isNotEmpty()) {
                                                onRenameGroup(group.groupId, normalized)
                                                renameGroupId = null
                                                renameValue = ""
                                            }
                                        },
                                    )
                                    TvManagementActionRow(
                                        label = "Cancel",
                                        compact = true,
                                        onClick = {
                                            renameGroupId = null
                                            renameValue = ""
                                        },
                                    )
                                }
                                deleteGroupId == group.groupId -> {
                                    TvManagementActionRow(
                                        label = "Cancel",
                                        compact = true,
                                        onClick = { deleteGroupId = null },
                                    )
                                    TvManagementActionRow(
                                        label = "Confirm delete",
                                        compact = true,
                                        onClick = {
                                            onDeleteGroup(group.groupId)
                                            deleteGroupId = null
                                        },
                                    )
                                }
                                else -> {
                                    TvManagementActionRow(
                                        label = "Add selected",
                                        enabled = selectedChannelIds.isNotEmpty(),
                                        compact = true,
                                        onClick = { onAddSelectionToGroup(group.groupId) },
                                    )
                                    TvManagementActionRow(
                                        label = "Remove selected",
                                        enabled = selectedChannelIds.isNotEmpty(),
                                        compact = true,
                                        onClick = { onRemoveSelectionFromGroup(group.groupId) },
                                    )
                                    TvManagementActionRow(
                                        label = "Rename",
                                        compact = true,
                                        onClick = {
                                            renameGroupId = group.groupId
                                            renameValue = group.name
                                        },
                                    )
                                    TvManagementActionRow(
                                        label = "Delete",
                                        compact = true,
                                        onClick = { deleteGroupId = group.groupId },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvChannelCustomizationPage(
    channel: LiveChannelItem,
    onSetLocalDisplayName: (String) -> Unit,
    onClearLocalDisplayName: () -> Unit,
    onSetLogoOverride: (String) -> Unit,
    onClearLogoOverride: () -> Unit,
    onDone: () -> Unit,
) {
    var localName by remember(channel.channelId) { mutableStateOf(channel.localDisplayName.orEmpty()) }
    var logoValue by remember(channel.channelId) { mutableStateOf("") }
    val localNameFocusRequester = remember { FocusRequester() }
    val doneFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        localNameFocusRequester.requestFocus()
    }

    TvManagementSubpage(
        title = "Customize channel",
        description = "Local presentation only. Provider data is not modified.",
        onDone = onDone,
        doneFocusRequester = doneFocusRequester,
    ) {
        Text(channel.providerName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        TvManagementTextInput(
            value = localName,
            onValueChange = { localName = it },
            placeholder = "Local channel name",
            focusRequester = localNameFocusRequester,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvManagementActionRow(
                label = "Save name",
                enabled = localName.trim().isNotEmpty() && localName.trim() != channel.localDisplayName?.trim(),
                compact = true,
                onClick = { onSetLocalDisplayName(localName.trim()) },
            )
            TvManagementActionRow(
                label = "Use provider name",
                enabled = channel.localDisplayName != null,
                compact = true,
                onClick = {
                    onClearLocalDisplayName()
                    localName = ""
                },
            )
        }
        TvManagementTextInput(
            value = logoValue,
            onValueChange = { logoValue = it },
            placeholder = "Logo URL or URI",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvManagementActionRow(
                label = "Set logo",
                enabled = logoValue.isNotBlank(),
                compact = true,
                onClick = {
                    val normalized = logoValue.trim()
                    if (normalized.isNotEmpty()) {
                        onSetLogoOverride(normalized)
                        logoValue = ""
                    }
                },
            )
            TvManagementActionRow(
                label = "Use provider logo",
                enabled = channel.hasLogoOverride,
                compact = true,
                onClick = onClearLogoOverride,
            )
        }
    }
}

@Composable
private fun TvManagementSubpage(
    title: String,
    description: String,
    onDone: () -> Unit,
    doneFocusRequester: FocusRequester,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TvManagementActionRow(
                label = "Done",
                onClick = onDone,
                focusRequester = doneFocusRequester,
                compact = true,
            )
        }
        content()
    }
}

@Composable
private fun TvManagementTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (focused) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.merge(
                TextStyle(
                    color = if (focused) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ),
            ),
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    },
                )
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun TvManagementActionRow(
    label: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    detail: String? = null,
    compact: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val rowModifier = if (compact) {
        Modifier.height(48.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .height(54.dp)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = rowModifier
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                },
            )
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(12.dp),
        color = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
            focused -> MaterialTheme.colorScheme.primaryContainer
            selected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        },
        contentColor = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            focused -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                modifier = if (detail != null) Modifier.weight(1f) else Modifier,
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TvManagementError(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    )
}
