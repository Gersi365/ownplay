package app.ownplay.player.source.m3u

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Versioned source locator stored behind the source's opaque secure-store reference.
 *
 * Legacy OwnPlay installs stored the raw remote URL or local content URI directly.
 * [parse] deliberately accepts those values and treats them as cleartext-disabled,
 * so introducing source-level transport policy does not require a Room migration.
 */
data class M3uSourceLocator(
    val value: String,
    val allowCleartext: Boolean,
) {
    init {
        require(value.isNotBlank()) { "M3U source locator value must not be blank" }
    }

    override fun toString(): String =
        "M3uSourceLocator(value=<redacted>, allowCleartext=$allowCleartext)"
}

object M3uSourceLocatorCodec {
    private const val PREFIX = "ownplay-m3u-source-v1"

    fun encode(locator: M3uSourceLocator): String {
        val encodedValue = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(locator.value.toByteArray(StandardCharsets.UTF_8))
        val cleartextFlag = if (locator.allowCleartext) "1" else "0"
        return "$PREFIX|$cleartextFlag|$encodedValue"
    }

    fun parse(storedValue: String): M3uSourceLocator? {
        val normalized = storedValue.trim()
        if (normalized.isEmpty()) return null
        if (!normalized.startsWith("$PREFIX|")) {
            return M3uSourceLocator(
                value = normalized,
                allowCleartext = false,
            )
        }

        val parts = normalized.split('|', limit = 3)
        if (parts.size != 3 || parts[0] != PREFIX) return null
        val allowCleartext = when (parts[1]) {
            "0" -> false
            "1" -> true
            else -> return null
        }
        val decoded = try {
            String(
                Base64.getUrlDecoder().decode(parts[2]),
                StandardCharsets.UTF_8,
            )
        } catch (_: IllegalArgumentException) {
            return null
        }.trim()
        if (decoded.isEmpty()) return null

        return M3uSourceLocator(
            value = decoded,
            allowCleartext = allowCleartext,
        )
    }
}
