package app.ownplay.player.persistence.sync

import androidx.room.withTransaction
import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.sync.PairingDeviceIdStore
import java.util.UUID

/**
 * Resolves the single durable sync device id used by pairing and normal sync mutations.
 *
 * Pairing can happen before the first personalization mutation on a fresh install, so this store
 * initializes device_sync_local_state when needed. The Room transaction prevents two concurrent
 * callers from creating different device identities.
 */
internal class RoomPairingDeviceIdStore(
    private val database: OwnPlayDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val deviceIdFactory: () -> String = { UUID.randomUUID().toString() },
) : PairingDeviceIdStore {
    override suspend fun getOrCreateDeviceId(): String = database.withTransaction {
        val dao = database.deviceSyncDao()
        dao.localState()?.deviceId ?: run {
            val timestamp = now().coerceAtLeast(0L)
            val deviceId = deviceIdFactory().trim()
            require(deviceId.isNotEmpty()) { "Pairing device id cannot be blank" }
            dao.upsertLocalState(
                DeviceSyncLocalStateEntity(
                    deviceId = deviceId,
                    nextRevision = 1L,
                    updatedAtEpochMillis = timestamp,
                ),
            )
            requireNotNull(dao.localState()).deviceId
        }
    }
}
