package app.ownplay.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    var working by remember(categories) { mutableStateOf(categories) }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var pointerY by remember { mutableStateOf<Float?>(null) }
    var dropTarget by remember { mutableStateOf<CategoryDropTarget?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun clearDrag() {
        draggedKey = null
        pointerY = null
        dropTarget = null
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Reorder categories",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Drag the handle. Hidden categories remain manageable here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(
                    items = working,
                    key = { _, category -> category.providerCategoryKey },
                ) { _, category ->
                    val key = category.providerCategoryKey
                    val isTarget = dropTarget?.anchorKey == key
                    val handleModifier = Modifier.pointerInput(key, working) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                val itemInfo = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { info -> info.key == key }
                                draggedKey = key
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
                                val layout = listState.layoutInfo
                                val edge = 72f
                                when {
                                    nextY < layout.viewportStartOffset + edge -> {
                                        scope.launch { listState.scrollBy(-36f) }
                                    }
                                    nextY > layout.viewportEndOffset - edge -> {
                                        scope.launch { listState.scrollBy(36f) }
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
                                    val next = moveRelative(
                                        categories = working,
                                        draggedKey = dragged,
                                        anchorKey = target.anchorKey,
                                        placement = target.placement,
                                    )
                                    if (next != working) {
                                        working = next
                                        onOrderChanged(next.map(LiveCategory::providerCategoryKey))
                                    }
                                }
                                clearDrag()
                            },
                            onDragCancel = ::clearDrag,
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            draggedKey == key -> MaterialTheme.colorScheme.surfaceVariant
                            isTarget -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "≡",
                                modifier = handleModifier
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                            if (isTarget) {
                                Text(
                                    text = if (dropTarget?.placement == ManualOrderPlacement.BEFORE) {
                                        "Before"
                                    } else {
                                        "After"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
