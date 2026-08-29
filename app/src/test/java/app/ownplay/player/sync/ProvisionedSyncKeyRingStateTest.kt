package app.ownplay.player.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisionedSyncKeyRingStateTest {
    @Test
    fun `state codec round trips keys peers and tombstone-free current state`() {
        val keyBytes = ByteArray(32) { index -> (index + 1).toByte() }
        val snapshot = ProvisionedSyncKeyRingSnapshot(
            currentKeyId = "key-1",
            keys = listOf(ProvisionedSyncKeyState("key-1", 3L, keyBytes.copyOf())),
            peers = listOf(
                ProvisionedPeerState(
                    deviceId = "tv",
                    identityFingerprint = "AA:BB:CC",
                    revoked = true,
                    knownKeyIds = setOf("key-1"),
                ),
            ),
        )

        val encoded = ProvisionedSyncKeyRingStateCodec.encode(snapshot)
        val decoded = ProvisionedSyncKeyRingStateCodec.decode(encoded)
        try {
            assertEquals("key-1", decoded.currentKeyId)
            assertEquals(3L, decoded.keys.single().epoch)
            assertArrayEquals(keyBytes, decoded.keys.single().keyBytes)
            assertEquals("tv", decoded.peers.single().deviceId)
            assertTrue(decoded.peers.single().revoked)
            assertEquals(setOf("key-1"), decoded.peers.single().knownKeyIds)
            assertTrue(decoded.toString().contains("keys=1"))
            assertFalse(decoded.toString().contains(keyBytes.joinToString()))
        } finally {
            decoded.wipe()
            snapshot.wipe()
            keyBytes.fill(0)
            encoded.fill(0)
        }
    }

    @Test
    fun `trusted peer and current group key survive key ring recreation`() {
        val pairing = pair("phone", "tv")
        val store = MemoryStateStore()
        val first = ProvisionedPortableSourceSecretKeyRing("phone", stateStore = store)
        val created = first.createProvisioningPackage(
            pairing.phone,
            pairing.phone.verification,
        ) as SyncKeyPackageCreationResult.Created
        val originalKeyId = first.currentKey().keyId
        val originalBytes = requireNotNull(first.currentKey().secretKey.encoded)

        val restored = ProvisionedPortableSourceSecretKeyRing("phone", stateStore = store)
        val restoredBytes = requireNotNull(restored.currentKey().secretKey.encoded)
        try {
            assertEquals(originalKeyId, restored.currentKey().keyId)
            assertEquals(1L, restored.currentEpoch())
            assertArrayEquals(originalBytes, restoredBytes)
            assertEquals(created.value.keyId, restored.currentKey().keyId)
            assertEquals(pairing.phone.peer.fingerprint, restored.trustedPeer("tv")?.identityFingerprint)
            assertEquals(setOf(originalKeyId), restored.trustedPeer("tv")?.knownKeyIds)
        } finally {
            originalBytes.fill(0)
            restoredBytes.fill(0)
        }
    }

    @Test
    fun `rotation revocation and retirement survive key ring recreation`() {
        val pairing = pair("phone", "tv")
        val store = MemoryStateStore()
        val first = ProvisionedPortableSourceSecretKeyRing("phone", stateStore = store)
        first.createProvisioningPackage(
            pairing.phone,
            pairing.phone.verification,
        ) as SyncKeyPackageCreationResult.Created
        val oldKeyId = first.currentKey().keyId
        val revoked = first.revokePeer("tv")
        val rotation = first.rotateCurrentKey()

        val afterRotation = ProvisionedPortableSourceSecretKeyRing("phone", stateStore = store)
        assertTrue(revoked.rotationRequired)
        assertTrue(afterRotation.trustedPeer("tv")?.revoked == true)
        assertEquals(2L, afterRotation.currentEpoch())
        assertEquals(rotation.newKeyId, afterRotation.currentKey().keyId)
        assertNotNull(afterRotation.keyForId(oldKeyId))
        assertTrue(afterRotation.retireKey(oldKeyId))

        val afterRetirement = ProvisionedPortableSourceSecretKeyRing("phone", stateStore = store)
        assertNull(afterRetirement.keyForId(oldKeyId))
        assertEquals(rotation.newKeyId, afterRetirement.currentKey().keyId)
        assertTrue(afterRetirement.trustedPeer("tv")?.revoked == true)
        assertFalse(afterRetirement.trustedPeer("tv")?.knownKeyIds?.contains(oldKeyId) == true)
    }

    @Test
    fun `restoration rejects key id that does not match persisted key material`() {
        val invalidStore = object : ProvisionedSyncKeyRingStateStore {
            override fun load(): ProvisionedSyncKeyRingSnapshot = ProvisionedSyncKeyRingSnapshot(
                currentKeyId = "wrong-key-id",
                keys = listOf(
                    ProvisionedSyncKeyState(
                        keyId = "wrong-key-id",
                        epoch = 1L,
                        keyBytes = ByteArray(32) { 7 },
                    ),
                ),
                peers = emptyList(),
            )

            override fun save(snapshot: ProvisionedSyncKeyRingSnapshot) = Unit
        }

        val result = runCatching {
            ProvisionedPortableSourceSecretKeyRing("phone", stateStore = invalidStore)
        }

        assertTrue(result.isFailure)
    }

    private fun pair(phoneId: String, tvId: String): PairedCandidates {
        val phone = PairingIdentityKey.generate(phoneId)
        val tv = PairingIdentityKey.generate(tvId)
        val initiator = DevicePairingProtocol.createOffer(phone)
        val responder = DevicePairingProtocol.answerOffer(tv, initiator.offer)
        val phoneResult = DevicePairingProtocol.acceptAnswer(initiator, responder.answer)
        val tvCandidate = DevicePairingProtocol.acceptConfirmation(
            responder,
            phoneResult.confirmation,
        )
        return PairedCandidates(phoneResult.candidate, tvCandidate)
    }

    private class MemoryStateStore : ProvisionedSyncKeyRingStateStore {
        private var payload: ByteArray? = null

        override fun load(): ProvisionedSyncKeyRingSnapshot? =
            payload?.let(ProvisionedSyncKeyRingStateCodec::decode)

        override fun save(snapshot: ProvisionedSyncKeyRingSnapshot) {
            payload?.fill(0)
            payload = ProvisionedSyncKeyRingStateCodec.encode(snapshot)
        }
    }

    private data class PairedCandidates(
        val phone: DevicePairingCandidate,
        val tv: DevicePairingCandidate,
    )
}
