#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KOTLINC_BIN="${KOTLINC_BIN:-$(command -v kotlinc || true)}"

if [[ -z "$KOTLINC_BIN" ]]; then
  echo "kotlinc is required for the dependency-free personalization core check." >&2
  exit 2
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

cat > "$TMP_DIR/PersonalizationCoreCheck.kt" <<'KOTLIN'
package app.ownplay.player.personalization

private fun requireEquals(expected: Any?, actual: Any?, label: String) {
    check(expected == actual) { "$label: expected=$expected actual=$actual" }
}

fun main() {
    val moved = ManualChannelOrderPlanner.moveRelative(
        currentOrder = listOf("one", "two", "three", "four"),
        channelId = "one",
        anchorChannelId = "three",
        placement = ManualOrderPlacement.AFTER,
    ) as ManualOrderPlanResult.Success
    requireEquals(
        listOf("two", "three", "one", "four"),
        moved.plan.channelIds,
        "relative move",
    )

    val top = ManualChannelOrderPlanner.moveSelectedToTop(
        currentOrder = listOf("one", "two", "three", "four"),
        selectedChannelIds = setOf("four", "two"),
    ) as ManualOrderPlanResult.Success
    requireEquals(listOf("two", "four", "one", "three"), top.plan.channelIds, "bulk top")

    val selection = ChannelSelectionValidator.validate(
        requestedChannelIds = setOf("four", "two"),
        availableChannelIdsInOrder = listOf("one", "two", "three", "four"),
    ) as ChannelSelectionValidationResult.Success
    requireEquals(listOf("two", "four"), selection.channelIds, "source-scoped selection")

    var edit = ChannelEditReducer.enter(ChannelEditState())
    edit = ChannelEditReducer.toggleSelection(edit, "two")
    edit = ChannelEditReducer.toggleSelection(edit, "four")
    requireEquals(setOf("two", "four"), edit.selectedChannelIds, "edit selection")

    val before = ChannelDragTargetResolver.resolve(
        pointerY = 124f,
        draggedChannelId = "two",
        visibleItems = listOf(
            VisibleChannelBounds("one", 0f, 60f),
            VisibleChannelBounds("two", 60f, 120f),
            VisibleChannelBounds("three", 120f, 180f),
        ),
    )
    requireEquals(
        ChannelDragTarget("three", ManualOrderPlacement.BEFORE),
        before,
        "drag target before",
    )

    val after = ChannelDragTargetResolver.resolve(
        pointerY = 151f,
        draggedChannelId = "one",
        visibleItems = listOf(
            VisibleChannelBounds("one", 0f, 60f),
            VisibleChannelBounds("three", 120f, 180f),
        ),
    )
    requireEquals(
        ChannelDragTarget("three", ManualOrderPlacement.AFTER),
        after,
        "drag target after",
    )

    println("PERSONALIZATION_CORE_CHECK=PASS")
}
KOTLIN

"$KOTLINC_BIN" \
  "$ROOT_DIR/app/src/main/java/app/ownplay/player/personalization/ManualChannelOrderPlanner.kt" \
  "$ROOT_DIR/app/src/main/java/app/ownplay/player/personalization/ChannelSelectionValidator.kt" \
  "$ROOT_DIR/app/src/main/java/app/ownplay/player/personalization/ChannelEditState.kt" \
  "$ROOT_DIR/app/src/main/java/app/ownplay/player/personalization/ChannelDragTargetResolver.kt" \
  "$TMP_DIR/PersonalizationCoreCheck.kt" \
  -include-runtime \
  -d "$TMP_DIR/personalization-core-check.jar"

java -jar "$TMP_DIR/personalization-core-check.jar"
