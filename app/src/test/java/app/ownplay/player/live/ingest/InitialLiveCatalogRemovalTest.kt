package app.ownplay.player.live.ingest

import app.ownplay.player.persistence.ChannelAvailability
import app.ownplay.player.persistence.ProviderCategoryEntity
import app.ownplay.player.persistence.ProviderChannelEntity
import app.ownplay.player.persistence.secure.SensitiveValueRef
import app.ownplay.player.persistence.secure.SensitiveValueStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialLiveCatalogRemovalTest {
    @Test
    fun `missing provider channel is retained as removed`() = runBlocking {
        val existing = listOf(
            channel("kept", "provider-kept", "ref-kept"),
            channel("removed", "provider-removed", "ref-removed"),
        )
        val persistence = FakePersistence(existing)
        val store = FakeSensitiveValueStore()
        val result = InitialLiveCatalogIngestor(persistence, store).ingest(
            sourceId = "source",
            generation = 1_000L,
            catalog = IncomingLiveCatalog(
                categories = emptyList(),
                channels = listOf(
                    IncomingLiveChannel(
                        providerKey = "provider-kept",
                        providerStreamId = "1",
                        providerCategoryKey = null,
                        providerName = "Kept",
                        tvgId = null,
                        tvgName = null,
                        locatorValue = "ownplay-locator-v1|direct|https://example.test/kept",
                        logoValue = null,
                        providerOrder = 0,
                    ),
                ),
            ),
        )

        assertEquals(InitialLiveCatalogIngestResult.Success(0, 1), result)
        assertEquals(2, persistence.savedChannels.size)
        assertEquals(
            ChannelAvailability.AVAILABLE,
            persistence.savedChannels.single { it.channelId == "kept" }.availability,
        )
        val removed = persistence.savedChannels.single { it.channelId == "removed" }
        assertEquals(ChannelAvailability.REMOVED, removed.availability)
        assertEquals("ref-removed", removed.streamLocatorRef)
    }

    @Test
    fun `removed channel becomes available again when provider returns it`() = runBlocking {
        val persistence = FakePersistence(
            listOf(
                channel(
                    channelId = "returning",
                    providerKey = "provider-returning",
                    streamRef = "old-ref",
                    availability = ChannelAvailability.REMOVED,
                ),
            ),
        )
        val result = InitialLiveCatalogIngestor(persistence, FakeSensitiveValueStore()).ingest(
            sourceId = "source",
            generation = 2_000L,
            catalog = IncomingLiveCatalog(
                categories = emptyList(),
                channels = listOf(
                    IncomingLiveChannel(
                        providerKey = "provider-returning",
                        providerStreamId = "2",
                        providerCategoryKey = null,
                        providerName = "Returning",
                        tvgId = null,
                        tvgName = null,
                        locatorValue = "ownplay-locator-v1|direct|https://example.test/returning",
                        logoValue = null,
                        providerOrder = 0,
                    ),
                ),
            ),
        )

        assertTrue(result is InitialLiveCatalogIngestResult.Success)
        val saved = persistence.savedChannels.single()
        assertEquals("returning", saved.channelId)
        assertEquals(ChannelAvailability.AVAILABLE, saved.availability)
        assertEquals(2_000L, saved.lastSeenGeneration)
    }

    private fun channel(
        channelId: String,
        providerKey: String,
        streamRef: String,
        availability: String = ChannelAvailability.AVAILABLE,
    ) = ProviderChannelEntity(
        channelId = channelId,
        sourceId = "source",
        providerKey = providerKey,
        providerStreamId = null,
        providerCategoryKey = null,
        providerName = channelId,
        tvgId = null,
        tvgName = null,
        logoRef = null,
        streamLocatorRef = streamRef,
        providerOrder = 0,
        availability = availability,
        lastSeenGeneration = 10L,
    )

    private class FakePersistence(
        private val existing: List<ProviderChannelEntity>,
    ) : LiveCatalogPersistence {
        var savedChannels: List<ProviderChannelEntity> = emptyList()

        override suspend fun existingChannels(sourceId: String): List<ProviderChannelEntity> = existing

        override suspend fun applyInitialCatalog(
            categories: List<ProviderCategoryEntity>,
            channels: List<ProviderChannelEntity>,
        ) {
            savedChannels = channels
        }
    }

    private class FakeSensitiveValueStore : SensitiveValueStore {
        private val values = linkedMapOf<String, String>()
        private var nextId = 1

        override fun put(value: String): SensitiveValueRef {
            val ref = SensitiveValueRef("new-ref-${nextId++}")
            values[ref.value] = value
            return ref
        }

        override fun get(ref: SensitiveValueRef): String? = values[ref.value]

        override fun delete(ref: SensitiveValueRef) {
            values.remove(ref.value)
        }
    }
}
