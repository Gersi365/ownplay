package app.ownplay.player.testing

import java.io.File

internal fun sourceText(relativePath: String): String {
    val candidates = listOf(
        File(relativePath),
        File("app/$relativePath"),
    )
    return candidates.firstOrNull(File::isFile)?.readText()
        ?: error("Could not locate source file: $relativePath")
}

internal fun normalizedSource(source: String): String =
    source.replace(Regex("\\s+"), " ").trim()

/**
 * Extracts the brace-delimited block following [anchor].
 *
 * Unlike substringBefore/substringAfter source contracts, this remains stable when nested blocks are
 * reordered or expanded. Braces inside strings and comments are ignored so harmless copy changes do
 * not accidentally terminate the extracted block.
 */
internal fun sourceBlockAfter(source: String, anchor: String): String {
    val anchorIndex = source.indexOf(anchor)
    require(anchorIndex >= 0) { "Could not locate source anchor: $anchor" }

    val openingBrace = source.indexOf('{', startIndex = anchorIndex + anchor.length)
    require(openingBrace >= 0) { "Could not locate opening brace after source anchor: $anchor" }

    val closingBrace = findClosingBrace(source, openingBrace)
    return source.substring(openingBrace + 1, closingBrace)
}

private fun findClosingBrace(source: String, openingBrace: Int): Int {
    var depth = 0
    var index = openingBrace
    var mode = ScanMode.CODE
    var blockCommentDepth = 0

    while (index < source.length) {
        val current = source[index]
        val next = source.getOrNull(index + 1)

        when (mode) {
            ScanMode.CODE -> when {
                current == '/' && next == '/' -> {
                    mode = ScanMode.LINE_COMMENT
                    index += 2
                    continue
                }
                current == '/' && next == '*' -> {
                    mode = ScanMode.BLOCK_COMMENT
                    blockCommentDepth = 1
                    index += 2
                    continue
                }
                current == '"' && source.startsWith("\"\"\"", index) -> {
                    mode = ScanMode.TRIPLE_STRING
                    index += 3
                    continue
                }
                current == '"' -> {
                    mode = ScanMode.STRING
                    index += 1
                    continue
                }
                current == '\'' -> {
                    mode = ScanMode.CHAR
                    index += 1
                    continue
                }
                current == '{' -> depth += 1
                current == '}' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
            ScanMode.STRING -> when {
                current == '\\' -> {
                    index += 2
                    continue
                }
                current == '"' -> mode = ScanMode.CODE
            }
            ScanMode.CHAR -> when {
                current == '\\' -> {
                    index += 2
                    continue
                }
                current == '\'' -> mode = ScanMode.CODE
            }
            ScanMode.TRIPLE_STRING -> if (source.startsWith("\"\"\"", index)) {
                mode = ScanMode.CODE
                index += 3
                continue
            }
            ScanMode.LINE_COMMENT -> if (current == '\n') {
                mode = ScanMode.CODE
            }
            ScanMode.BLOCK_COMMENT -> when {
                current == '/' && next == '*' -> {
                    blockCommentDepth += 1
                    index += 2
                    continue
                }
                current == '*' && next == '/' -> {
                    blockCommentDepth -= 1
                    index += 2
                    if (blockCommentDepth == 0) mode = ScanMode.CODE
                    continue
                }
            }
        }
        index += 1
    }

    error("Unbalanced source block starting at index $openingBrace")
}

private enum class ScanMode {
    CODE,
    STRING,
    CHAR,
    TRIPLE_STRING,
    LINE_COMMENT,
    BLOCK_COMMENT,
}
