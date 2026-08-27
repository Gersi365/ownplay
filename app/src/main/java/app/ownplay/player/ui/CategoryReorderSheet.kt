package app.ownplay.player.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.ownplay.player.live.LiveCategory
import app.ownplay.player.personalization.ManualOrderPlacement
import kotlinx.coroutines.launch

private data class CategoryDropTarget(
    val anchorKey: String,
    val placement: ManualOrderPlacement,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryReorderSheet(
    categories: List<LiveCategory>,
    onOrderChanged: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    var working by remember(categories) { mutableStateOf(categories) }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var pointerY by remember { mutableStateOf<Float?>(null) }
    var dragVisualOffsetY by remember { mutableFloatStateOf(0f) }
    var dropTarget by remember { mutableStateOf<CategoryDropTarget?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun clearDrag() {
        draggedKey = null
        pointerY = null
        dragVisualOffsetY = 0f
        dropTarget = null
    }

    fun applyOrder(next: List<LiveCategory>) {
        if (next == working) return
        working = next
        onOrderChanged(next.map(LiveCategory::providerCategoryKey))
    }

    fun moveWithRemote(index: Int, delta: Int) {
        val category = working.getOrNull(index) ?: return
        val anchor = working.getOrNull(index + delta) ?: return
        applyOrder(
            moveRelative(
                categories = working,
                draggedKey = category.providerCategoryKey,
                anchorKey = anchor.providerCategoryKey,
                placement = if (delta < 0) {
                    ManualOrderPlacement.BEFORE
                } else {
                    ManualOrderPlacement.AFTER
                },
            ),
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Reorder categories",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (isTelevision) {
                            "Use Up / Down with the remote. Press Done when finished."
                        } else {
                            "Hold the handle, then move the category to its new position."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isTelevision) {
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(
                    items = working,
                    key = { _, category -> category.providerCategoryKey },
                ) { index, category ->
                    val key = category.providerCategoryKey
                    val isDragging = draggedKey == key
                    val isTarget = dropTarget?.anchorKey == key
                    val handleModifier = if (isTelevision) {
                        Modifier
                    } else {
                        Modifier.pointerInput(key, working) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    val itemInfo = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { info -> info.key == key }
                                    draggedKey = key
                                    dragVisualOffsetY = 0f
                                    pointerY = itemInfo?.let { info ->
                                        info.offset + (info.size / 2f)
                                    }
                                    dropTarget = pointerY?.let { y ->
                                        resolveCategoryTarget(y, key, listState.layoutInfo.visibleItemsInfo)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val dragged = draggedKey ?: return@detectDragGesturesAfterLongPress
                                    val nextY = (pointerY ?: return@detectDragGesturesAfterLongPress) + dragAmount.y
                                    pointerY = nextY
                                    dragVisualOffsetY += dragAmount.y
                                    val layout = listState.layoutInfo
                                    val edge = 72f
                                    val scrollDelta = when {
                                        nextY < layout.viewportStartOffset + edge -> -36f
                                        nextY > layout.viewportEndOffset - edge -> 36f
                                        else -> 0f
                                    }
                                    if (scrollDelta != 0f) {
                                        scope.launch {
                                            val consumed = listState.scrollBy(scrollDelta)
                                            dragVisualOffsetY += consumed
                                        }
                                    }
                                    dropTarget = resolveCategoryTarget(
                                        pointerY = nextY,
                                        draggedKey = dragged,
                                        visibleItems = layout.visibleItemsInfo,
                                    )
                                },
                                onDragEnd = {
                                    val dragged = draggedKey
                                    val target = dropTarget
                                    if (dragged != null && target != null) {
                                        applyOrder(
                                            moveRelative(
                                                categories = working,
                                                draggedKey = dragged,
                                                anchorKey = target.anchorKey,
                                                placement = target.placement,
                                            ),
                                        )
                                    }
                                    clearDrag()
                                },
                                onDragCancel = ::clearDrag,
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (
                            isTarget &&
                            dropTarget?.placement == ManualOrderPlacement.BEFORE
                        ) {
                            CategoryInsertionIndicator(
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 2f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragVisualOffsetY else 0f
                                    scaleX = if (isDragging) 1.025f else 1f
                                    scaleY = if (isDragging) 1.025f else 1f
                                    alpha = if (isDragging) 0.98f else 1f
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                isDragging -> MaterialTheme.colorScheme.primaryContainer
                                isTarget -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.surface
                            },
                            shadowElevation = if (isDragging) 12.dp else 0.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (!isTelevision) {
                                    Text(
                                        text = "≡",
                                        modifier = handleModifier
                                            .background(
                                                color = if (isDragging) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = if (isDragging) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (category.isHidden) {
                                        Text(
                                            text = "Hidden",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (isTelevision) {
                                    TextButton(onClick = { moveWithRemote(index, -1) }) {
                                        Text("Up")
                                    }
                                    TextButton(onClick = { moveWithRemote(index, 1) }) {
                                        Text("Down")
                                    }
                                } else if (isDragging) {
                                    Text(
                                        text = "MOVING",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }

                        if (
                            isTarget &&
                            dropTarget?.placement == ManualOrderPlacement.AFTER
                        ) {
                            CategoryInsertionIndicator(
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryInsertionIndicator(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .zIndex(3f),
        thickness = 3.dp,
        color = MaterialTheme.colorScheme.primary,
    )
}

private fun resolveCategoryTarget(
    pointerY: Float,
    draggedKey: String,
    visibleItems: List<androidx.compose.foundation.lazy.LazyListItemInfo>,
): CategoryDropTarget? {
    val candidates = visibleItems.mapNotNull { info ->
        val key = info.key as? String ?: return@mapNotNull null
        if (key == draggedKey) return@mapNotNull null
        info to key
    }
    val containing = candidates.firstOrNull { (info, _) ->
        pointerY >= info.offset && pointerY <= info.offset + info.size
    } ?: candidates.minByOrNull { (info, _) ->
        kotlin.math.abs(pointerY - (info.offset + info.size / 2f))
    } ?: return null
    val (info, key) = containing
    return CategoryDropTarget(
        anchorKey = key,
        placement = if (pointerY < info.offset + info.size / 2f) {
            ManualOrderPlacement.BEFORE
        } else {
            ManualOrderPlacement.AFTER
        },
    )
}

internal fun moveRelative(
    categories: List<LiveCategory>,
    draggedKey: String,
    anchorKey: String,
    placement: ManualOrderPlacement,
): List<LiveCategory> {
    if (draggedKey == anchorKey) return categories
    val dragged = categories.firstOrNull { it.providerCategoryKey == draggedKey } ?: return categories
    if (categories.none { it.providerCategoryKey == anchorKey }) return categories
    val without = categories.filterNot { it.providerCategoryKey == draggedKey }.toMutableList()
    val anchorIndex = without.indexOfFirst { it.providerCategoryKey == anchorKey }
    if (anchorIndex < 0) return categories
    val insertionIndex = if (placement == ManualOrderPlacement.BEFORE) {
        anchorIndex
    } else {
        anchorIndex + 1
    }
    without.add(insertionIndex.coerceIn(0, without.size), dragged)
    return without
}
