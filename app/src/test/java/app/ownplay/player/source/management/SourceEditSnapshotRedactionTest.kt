package app.ownplay.player.source.management

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceEditSnapshotRedactionTest {
    @Test
    fun toStringRedactsEndpointAndSourceId() {
        val snapshot = SourceEditSnapshot(
            sourceId = "source-sensitive-id",
            name = "Living Room",
            sourceKind = "remote_m3u",
            endpoint = "https://example.test/list.m3u?token=secret-token",
            allowCleartext = true,
        )

        val rendered = snapshot.toString()

        assertFalse(rendered.contains("source-sensitive-id"))
        assertFalse(rendered.contains("example.test"))
        assertFalse(rendered.contains("secret-token"))
        assertTrue(rendered.contains("sourceId=<opaque>"))
        assertTrue(rendered.contains("endpoint=<redacted>"))
        assertTrue(rendered.contains("sourceKind=remote_m3u"))
        assertTrue(rendered.contains("allowCleartext=true"))
    }

    @Test
    fun toStringKeepsNullEndpointExplicit() {
        val snapshot = SourceEditSnapshot(
            sourceId = "source-sensitive-id",
            name = "Local file",
            sourceKind = "local_m3u",
            endpoint = null,
            allowCleartext = false,
        )

        val rendered = snapshot.toString()

        assertFalse(rendered.contains("source-sensitive-id"))
        assertTrue(rendered.contains("endpoint=null"))
    }
}
