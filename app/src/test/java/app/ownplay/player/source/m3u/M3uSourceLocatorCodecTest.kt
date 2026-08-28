package app.ownplay.player.source.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uSourceLocatorCodecTest {
    @Test
    fun `versioned locator round trips remote URL with query`() {
        val original = M3uSourceLocator(
            value = "http://example.test/list.m3u?username=user&token=a|b",
            allowCleartext = true,
        )

        assertEquals(original, M3uSourceLocatorCodec.parse(M3uSourceLocatorCodec.encode(original)))
    }

    @Test
    fun `versioned locator round trips local content uri`() {
        val original = M3uSourceLocator(
            value = "content://com.example.documents/document/playlist%3A42",
            allowCleartext = false,
        )

        assertEquals(original, M3uSourceLocatorCodec.parse(M3uSourceLocatorCodec.encode(original)))
    }

    @Test
    fun `legacy raw locator remains readable and defaults cleartext off`() {
        val parsed = M3uSourceLocatorCodec.parse("https://example.test/list.m3u")

        assertEquals("https://example.test/list.m3u", parsed?.value)
        assertFalse(parsed?.allowCleartext ?: true)
    }

    @Test
    fun `versioned locator never exposes raw source value in encoded form`() {
        val secretUrl = "https://example.test/list.m3u?token=secret-value"
        val encoded = M3uSourceLocatorCodec.encode(
            M3uSourceLocator(secretUrl, allowCleartext = false),
        )

        assertFalse(encoded.contains(secretUrl))
        assertFalse(encoded.contains("secret-value"))
        assertTrue(encoded.startsWith("ownplay-m3u-source-v1|0|"))
    }

    @Test
    fun `malformed versioned values are rejected instead of treated as legacy`() {
        assertNull(M3uSourceLocatorCodec.parse("ownplay-m3u-source-v1|2|abc"))
        assertNull(M3uSourceLocatorCodec.parse("ownplay-m3u-source-v1|1|***"))
        assertNull(M3uSourceLocatorCodec.parse("   "))
    }
}
