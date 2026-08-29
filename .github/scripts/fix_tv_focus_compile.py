from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file_path = Path(path)
    text = file_path.read_text(encoding='utf-8')
    if text.count(old) != 1:
        raise RuntimeError(f'{path}: expected exactly one target for {old[:80]!r}, found {text.count(old)}')
    file_path.write_text(text.replace(old, new, 1), encoding='utf-8')


series_path = 'app/src/main/java/app/ownplay/player/ui/library/LibrarySeriesComponents.kt'
replace_once(
    series_path,
    '    returnFocusEpisodeId: String?,\n    returnFocusGeneration: Int,\n',
    '    returnFocusEpisodeId: String? = null,\n    returnFocusGeneration: Int = 0,\n',
)

library_path = 'app/src/main/java/app/ownplay/player/ui/library/UnifiedLibraryRoute.kt'
replace_once(
    library_path,
    '    var currentlyFocusedItemKey by remember { mutableStateOf<String?>(null) }\n    val focusIndex = remember(focusKeys, focusItemKey) { focusKeys.indexOf(focusItemKey) }\n',
    '    var currentlyFocusedItemKey by remember { mutableStateOf<String?>(null) }\n    val focusRingColor = MaterialTheme.colorScheme.primary\n    val focusIndex = remember(focusKeys, focusItemKey) { focusKeys.indexOf(focusItemKey) }\n',
)
replace_once(
    library_path,
    '                color = MaterialTheme.colorScheme.primary,\n                shape = RoundedCornerShape(12.dp),\n',
    '                color = focusRingColor,\n                shape = RoundedCornerShape(12.dp),\n',
)

print('Applied TV focus compile fixes')
