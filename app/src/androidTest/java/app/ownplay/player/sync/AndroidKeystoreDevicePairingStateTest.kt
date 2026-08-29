package app.ownplay.player.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreDevicePairingStateTest {
    @Test
    fun identityTrustAndGroupKeySurviveStateOwnerRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val phoneDeviceId = "phone-${UUID.randomUUID()}"
        val tvIdentity = PairingIdentityKey.generate("tv-${UUID.randomUUID()}")
        val first = AndroidSecureDevicePairingState(context, phoneDeviceId)
        val firstFingerprint = first.identityKey.identity.fingerprint
        val pairing = pair(first.identityKey, tvIdentity)
        val created = first.keyRing.createProvisioningPackage(
            pairing.phone,
            pairing.phone.verification,
        ) as SyncKeyPackageCreationResult.Created
        val firstKeyBytes = requireNotNull(first.keyRing.currentKey().secretKey.encoded)

        val restored = AndroidSecureDevicePairingState(context, phoneDeviceId)
        val restoredKeyBytes = requireNotNull(restored.keyRing.currentKey().secretKey.encoded)
        try {
            assertEquals(firstFingerprint, restored.identityKey.identity.fingerprint)
            assertEquals(created.value.keyId, restored.keyRing.currentKey().keyId)
            assertEquals(1L, restored.keyRing.currentEpoch())
            assertArrayEquals(firstKeyBytes, restoredKeyBytes)
            assertEquals(
                tvIdentity.identity.fingerprint,
                restored.keyRing.trustedPeer(tvIdentity.identity.deviceId)?.identityFingerprint,
            )
            assertNotNull(restored.keyRing.trustedPeer(tvIdentity.identity.deviceId))

            val nextOffer = DevicePairingProtocol.createOffer(restored.identityKey)
            assertEquals(phoneDeviceId, nextOffer.offer.initiator.deviceId)
            assertEquals(
                restored.identityKey.identity.identityPublicKeyBase64Url,
                nextOffer.offer.initiator.identityPublicKeyBase64Url,
            )
        } finally {
            firstKeyBytes.fill(0)
            restoredKeyBytes.fill(0)
        }
    }

    @Test
    fun revokedPeerAndRotatedKeyRemainDurable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val phoneDeviceId = "phone-${UUID.randomUUID()}"
        val tvIdentity = PairingIdentityKey.generate("tv-${UUID.randomUUID()}")
        val first = AndroidSecureDevicePairingState(context, phoneDeviceId)
        val pairing = pair(first.identityKey, tvIdentity)
        first.keyRing.createProvisioningPackage(
            pairing.phone,
            pairing.phone.verification,
        ) as SyncKeyPackageCreationResult.Created
        val oldKeyId = first.keyRing.currentKey().keyId
        assertTrue(first.keyRing.revokePeer(tvIdentity.identity.deviceId).rotationRequired)
        val rotation = first.keyRing.rotateCurrentKey()

        val restored = AndroidSecureDevicePairingState(context, phoneDeviceId)
        assertTrue(restored.keyRing.trustedPeer(tvIdentity.identity.deviceId)?.revoked == true)
        assertEquals(rotation.newKeyId, restored.keyRing.currentKey().keyId)
        assertEquals(2L, restored.keyRing.currentEpoch())
        assertNotNull(restored.keyRing.keyForId(oldKeyId))
    }

    private fun pair(
        phone: PairingIdentityKey,
        tv: PairingIdentityKey,
    ): PairedCandidates {
        val initiator = DevicePairingProtocol.createOffer(phone)
        val responder = DevicePairingProtocol.answerOffer(tv, initiator.offer)
        val phoneResult = DevicePairingProtocol.acceptAnswer(initiator, responder.answer)
        val tvCandidate = DevicePairingProtocol.acceptConfirmation(
            responder,
            phoneResult.confirmation,
        )
        return PairedCandidates(phoneResult.candidate, tvCandidate)
    }

    private data class PairedCandidates(
        val phone: DevicePairingCandidate,
        val tv: DevicePairingCandidate,
    )
}
