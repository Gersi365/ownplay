package app.ownplay.player.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

internal const val PROVISIONED_SYNC_KEY_RING_STATE_VERSION = 1
private const val STATE_KEY_BYTES = 32
private const val MAX_KEYS = 64
private const val MAX_PEERS = 256
private const val MAX_KNOWN_KEYS_PER_PEER = 64
private const val MAX_STRING_BYTES = 8 * 1024

internal data class ProvisionedSyncKeyState(
    val keyId: String,
    val epoch: Long,
    val keyBytes: ByteArray,
) {
    init {
        require(keyId.isNotBlank())
        require(epoch > 0L)
        require(keyBytes.size == STATE_KEY_BYTES)
    }

    fun wipe() {
        keyBytes.fill(0)
    }

    override fun toString(): String =
        "ProvisionedSyncKeyState(keyId=<opaque>, epoch=$epoch, keyBytes=<redacted>)"
}

internal data class ProvisionedPeerState(
    val deviceId: String,
    val identityFingerprint: String,
    val revoked: Boolean,
    val knownKeyIds: Set<String>,
) {
    init {
        require(deviceId.isNotBlank())
        require(identityFingerprint.isNotBlank())
        require(knownKeyIds.size <= MAX_KNOWN_KEYS_PER_PEER)
        require(knownKeyIds.none(String::isBlank))
    }
}

internal data class ProvisionedSyncKeyRingSnapshot(
    val version: Int = PROVISIONED_SYNC_KEY_RING_STATE_VERSION,
    val currentKeyId: String?,
    val keys: List<ProvisionedSyncKeyState>,
    val peers: List<ProvisionedPeerState>,
) {
    init {
        require(version == PROVISIONED_SYNC_KEY_RING_STATE_VERSION)
        require(keys.size <= MAX_KEYS)
        require(peers.size <= MAX_PEERS)
        require(keys.map(ProvisionedSyncKeyState::keyId).distinct().size == keys.size)
        require(peers.map(ProvisionedPeerState::deviceId).distinct().size == peers.size)
        currentKeyId?.let { current -> require(keys.any { it.keyId == current }) }
    }

    fun wipe() {
        keys.forEach(ProvisionedSyncKeyState::wipe)
    }

    override fun toString(): String =
        "ProvisionedSyncKeyRingSnapshot(version=$version, currentKeyId=<opaque>, keys=${keys.size}, peers=${peers.size})"
}

internal interface ProvisionedSyncKeyRingStateStore {
    fun load(): ProvisionedSyncKeyRingSnapshot?
    fun save(snapshot: ProvisionedSyncKeyRingSnapshot)
}

/** Binary codec for the encrypted-at-rest key-ring payload. */
internal object ProvisionedSyncKeyRingStateCodec {
    fun encode(snapshot: ProvisionedSyncKeyRingSnapshot): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(snapshot.version)
                output.writeBoolean(snapshot.currentKeyId != null)
                snapshot.currentKeyId?.let { output.writeString(it) }

                output.writeInt(snapshot.keys.size)
                snapshot.keys.forEach { key ->
                    output.writeString(key.keyId)
                    output.writeLong(key.epoch)
                    output.writeInt(key.keyBytes.size)
                    output.write(key.keyBytes)
                }

                output.writeInt(snapshot.peers.size)
                snapshot.peers.forEach { peer ->
                    output.writeString(peer.deviceId)
                    output.writeString(peer.identityFingerprint)
                    output.writeBoolean(peer.revoked)
                    output.writeInt(peer.knownKeyIds.size)
                    peer.knownKeyIds.sorted().forEach { output.writeString(it) }
                }
            }
            bytes.toByteArray()
        }

    fun decode(payload: ByteArray): ProvisionedSyncKeyRingSnapshot =
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            val version = input.readInt()
            require(version == PROVISIONED_SYNC_KEY_RING_STATE_VERSION) {
                "Unsupported pairing state version"
            }
            val currentKeyId = if (input.readBoolean()) input.readString() else null

            val keyCount = input.readInt()
            require(keyCount in 0..MAX_KEYS) { "Pairing key count is invalid" }
            val keys = ArrayList<ProvisionedSyncKeyState>(keyCount)
            repeat(keyCount) {
                val keyId = input.readString()
                val epoch = input.readLong()
                val length = input.readInt()
                require(length == STATE_KEY_BYTES) { "Pairing key length is invalid" }
                val keyBytes = ByteArray(length).also(input::readFully)
                keys += ProvisionedSyncKeyState(keyId, epoch, keyBytes)
            }

            val peerCount = input.readInt()
            require(peerCount in 0..MAX_PEERS) { "Pairing peer count is invalid" }
            val peers = ArrayList<ProvisionedPeerState>(peerCount)
            repeat(peerCount) {
                val deviceId = input.readString()
                val fingerprint = input.readString()
                val revoked = input.readBoolean()
                val knownKeyCount = input.readInt()
                require(knownKeyCount in 0..MAX_KNOWN_KEYS_PER_PEER) {
                    "Pairing peer key count is invalid"
                }
                val knownKeys = linkedSetOf<String>()
                repeat(knownKeyCount) { knownKeys += input.readString() }
                peers += ProvisionedPeerState(deviceId, fingerprint, revoked, knownKeys)
            }

            require(input.available() == 0) { "Pairing state has trailing data" }
            ProvisionedSyncKeyRingSnapshot(version, currentKeyId, keys, peers)
        }

    private fun DataOutputStream.writeString(value: String) {
        val encoded = value.toByteArray(StandardCharsets.UTF_8)
        try {
            require(encoded.size <= MAX_STRING_BYTES) { "Pairing state string is too large" }
            writeInt(encoded.size)
            write(encoded)
        } finally {
            encoded.fill(0)
        }
    }

    private fun DataInputStream.readString(): String {
        val length = readInt()
        require(length in 0..MAX_STRING_BYTES) { "Pairing state string length is invalid" }
        val encoded = ByteArray(length).also(::readFully)
        return try {
            String(encoded, StandardCharsets.UTF_8)
        } finally {
            encoded.fill(0)
        }
    }
}
