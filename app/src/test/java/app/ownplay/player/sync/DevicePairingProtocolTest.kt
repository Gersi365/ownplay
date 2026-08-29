package app.ownplay.player.sync

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePairingProtocolTest {
    @Test
    fun `phone and tv derive identical authenticated pairing session`() {
        val pairing = pair("phone", "tv")

        assertEquals(pairing.phone.verification, pairing.tv.verification)
        assertEquals(pairing.phone.transcriptHashBase64Url, pairing.tv.transcriptHashBase64Url)
        assertEquals("tv", pairing.phone.peer.deviceId)
        assertEquals("phone", pairing.tv.peer.deviceId)

        val phoneKey = pairing.phone.sessionKey.copyBytes()
        val tvKey = pairing.tv.sessionKey.copyBytes()
        try {
            assertArrayEquals(phoneKey, tvKey)
            assertEquals(32, phoneKey.size)
        } finally {
            phoneKey.fill(0)
            tvKey.fill(0)
        }
    }

    @Test
    fun `tampered responder transcript is rejected`() {
        val phone = PairingIdentityKey.generate("phone")
        val tv = PairingIdentityKey.generate("tv")
        val initiator = DevicePairingProtocol.createOffer(phone)
        val responder = DevicePairingProtocol.answerOffer(tv, initiator.offer)
        val tampered = responder.answer.copy(
            responderNonceBase64Url = mutateBase64Url(responder.answer.responderNonceBase64Url),
        )

        val result = runCatching { DevicePairingProtocol.acceptAnswer(initiator, tampered) }

        assertTrue(result.isFailure)
    }

    @Test
    fun `tampered initiator offer signature is rejected`() {
        val phone = PairingIdentityKey.generate("phone")
        val tv = PairingIdentityKey.generate("tv")
        val initiator = DevicePairingProtocol.createOffer(phone)
        val tampered = initiator.offer.copy(
            initiatorNonceBase64Url = mutateBase64Url(initiator.offer.initiatorNonceBase64Url),
        )

        val result = runCatching { DevicePairingProtocol.answerOffer(tv, tampered) }

        assertTrue(result.isFailure)
    }

    @Test
    fun `verification is bound to fresh pairing transcript`() {
        val phone = PairingIdentityKey.generate("phone")
        val tv = PairingIdentityKey.generate("tv")
        val first = pair(phone, tv)
        val second = pair(phone, tv)

        assertNotEquals(first.phone.transcriptHashBase64Url, second.phone.transcriptHashBase64Url)
        assertNotEquals(first.phone.verification.fingerprint, second.phone.verification.fingerprint)
    }

    @Test
    fun `existing group sync key is provisioned from phone to tv`() {
        val pairing = pair("phone", "tv")
        val phoneRing = ProvisionedPortableSourceSecretKeyRing("phone")
        val tvRing = ProvisionedPortableSourceSecretKeyRing("tv")
        val phoneKey = phoneRing.bootstrapIfNeeded()

        val created = phoneRing.createProvisioningPackage(
            pairing.phone,
            pairing.phone.verification,
        ) as SyncKeyPackageCreationResult.Created
        val accepted = tvRing.acceptProvisioningPackage(
            pairing.tv,
            pairing.tv.verification,
            created.value,
        ) as SyncKeyPackageAcceptanceResult.Accepted

        assertEquals(phoneKey.keyId, accepted.keyId)
        assertEquals(phoneKey.keyId, tvRing.currentKey().keyId)
        val left = requireNotNull(phoneKey.secretKey.encoded)
        val right = requireNotNull(tvRing.currentKey().secretKey.encoded)
        try {
            assertArrayEquals(left, right)
        } finally {
            left.fill(0)
            right.fill(0)
        }
        assertEquals(PairingPeerTrustResult.Trusted, created.peerTrust)
        assertEquals(PairingPeerTrustResult.Trusted, accepted.peerTrust)
    }

    @Test
    fun `wrong human verification code cannot provision key`() {
        val pairing = pair("phone", "tv")
        val phoneRing = ProvisionedPortableSourceSecretKeyRing("phone")
        val wrongCode = if (pairing.phone.verification.shortCode == "00000000") "00000001" else "00000000"
        val receipt = pairing.phone.verification.copy(shortCode = wrongCode)

        val result = phoneRing.createProvisioningPackage(pairing.phone, receipt)

        assertEquals(SyncKeyPackageCreationResult.VerificationMismatch, result)
    }

    @Test
    fun `wrong transcript fingerprint cannot provision key`() {
        val pairing = pair("phone", "tv")
        val phoneRing = ProvisionedPortableSourceSecretKeyRing("phone")
        val wrongFingerprint = if (pairing.phone.verification.fingerprint == "0000-0000-0000-0000") {
            "0000-0000-0000-0001"
        } else {
            "0000-0000-0000-0000"
        }
        val receipt = pairing.phone.verification.copy(fingerprint = wrongFingerprint)

        val result = phoneRing.createProvisioningPackage(pairing.phone, receipt)

        assertEquals(SyncKeyPackageCreationResult.VerificationMismatch, result)
    }

    @Test
    fun `provisioning package is bound to pairing session and recipient`() {
        val first = pair("phone", "tv")
        val second = pair("phone", "tv")
        val phoneRing = ProvisionedPortableSourceSecretKeyRing("phone")
        val tvRing = ProvisionedPortableSourceSecretKeyRing("tv")
        val created = phoneRing.createProvisioningPackage(
            first.phone,
            first.phone.verification,
        ) as SyncKeyPackageCreationResult.Created

        val replay = tvRing.acceptProvisioningPackage(
            second.tv,
            second.tv.verification,
            created.value,
        )

        assertEquals(SyncKeyPackageAcceptanceResult.TranscriptMismatch, replay)
        assertNull(runCatching { tvRing.currentKey() }.getOrNull())
    }

    @Test
    fun `tampered provisioning ciphertext fails authenticated decryption`() {
        val pairing = pair("phone", "tv")
        val phoneRing = ProvisionedPortableSourceSecretKeyRing("phone")
        val tvRing = ProvisionedPortableSourceSecretKeyRing("tv")
        val created = phoneRing.createProvisioningPackage(
            pairing.phone,
            pairing.phone.verification,
        ) as SyncKeyPackageCreationResult.Created
        val tampered = created.value.copy(
            ciphertextBase64Url = mutateBase64Url(created.value.ciphertextBase64Url),
        )

        val result = tvRing.acceptProvisioningPackage(
            pairing.tv,
            pairing.tv.verification,
            tampered,
        )

        assertEquals(SyncKeyPackageAcceptanceResult.DecryptionFailure, result)
    }

    @Test
    fun `key rotation keeps previous key until explicit retirement`() {
        val pairing = pair("phone", "tv")
        val phoneRing = ProvisionedPortableSourceSecretKeyRing("phone")
        val tvRing = ProvisionedPortableSourceSecretKeyRing("tv")
        val initialPackage = phoneRing.createProvisioningPackage(
            pairing.phone,
            pairing.phone.verification,
        ) as SyncKeyPackageCreationResult.Created
        val initialAccepted = tvRing.acceptProvisioningPackage(
            pairing.tv,
            pairing.tv.verification,
            initialPackage.value,
        ) as SyncKeyPackageAcceptanceResult.Accepted
        val oldKeyId = initialAccepted.keyId

        val rotation = phoneRing.rotateCurrentKey()
        assertEquals(oldKeyId, rotation.previousKeyId)
        assertEquals(2L, rotation.newEpoch)
        val rotatedPackage = phoneRing.createProvisioningPackage(
            pairing.phone,
            pairing.phone.verification,
        ) as SyncKeyPackageCreationResult.Created
        val rotatedAccepted = tvRing.acceptProvisioningPackage(
            pairing.tv,
            pairing.tv.verification,
            rotatedPackage.value,
        ) as SyncKeyPackageAcceptanceResult.Accepted

        assertEquals(rotation.newKeyId, rotatedAccepted.keyId)
        assertNotNull(tvRing.keyForId(oldKeyId))
        assertEquals(rotation.newKeyId, tvRing.currentKey().keyId)
        assertTrue(tvRing.retireKey(oldKeyId))
        assertNull(tvRing.keyForId(oldKeyId))
    }

    @Test
    fun `revoked peer is blocked and signals group key rotation requirement`() {
        val pairing = pair("phone", "tv")
        val phoneRing = ProvisionedPortableSourceSecretKeyRing("phone")
        val initial = phoneRing.createProvisioningPackage(
            pairing.phone,
            pairing.phone.verification,
        ) as SyncKeyPackageCreationResult.Created
        val currentBeforeRevocation = phoneRing.currentKey().keyId
        assertEquals(currentBeforeRevocation, initial.value.keyId)

        val revoked = phoneRing.revokePeer("tv")
        val blocked = phoneRing.createProvisioningPackage(
            pairing.phone,
            pairing.phone.verification,
        )

        assertTrue(revoked.revoked)
        assertTrue(revoked.rotationRequired)
        assertEquals(SyncKeyPackageCreationResult.PeerRevoked, blocked)
        val rotation = phoneRing.rotateCurrentKey()
        assertEquals(currentBeforeRevocation, rotation.previousKeyId)
        assertNotNull(phoneRing.keyForId(currentBeforeRevocation))
        assertFalse(rotation.newKeyId == currentBeforeRevocation)
    }

    @Test
    fun `same device id with a different identity key is rejected`() {
        val phoneIdentity = PairingIdentityKey.generate("phone")
        val originalTvIdentity = PairingIdentityKey.generate("tv")
        val replacementTvIdentity = PairingIdentityKey.generate("tv")
        val first = pair(phoneIdentity, originalTvIdentity)
        val phoneRing = ProvisionedPortableSourceSecretKeyRing("phone")
        val trusted = phoneRing.createProvisioningPackage(
            first.phone,
            first.phone.verification,
        )
        assertTrue(trusted is SyncKeyPackageCreationResult.Created)

        val replacement = pair(phoneIdentity, replacementTvIdentity)
        val result = phoneRing.createProvisioningPackage(
            replacement.phone,
            replacement.phone.verification,
        )

        assertEquals(SyncKeyPackageCreationResult.PeerIdentityConflict, result)
        assertTrue(phoneRing.trustedPeer("tv")?.identityFingerprint == originalTvIdentity.identity.fingerprint)
    }

    @Test
    fun `sensitive key material is redacted from printable protocol objects`() {
        val pairing = pair("phone", "tv")
        val phoneRing = ProvisionedPortableSourceSecretKeyRing("phone")
        val created = phoneRing.createProvisioningPackage(
            pairing.phone,
            pairing.phone.verification,
        ) as SyncKeyPackageCreationResult.Created
        val raw = requireNotNull(phoneRing.currentKey().secretKey.encoded)
        val rawBase64 = try {
            Base64.getEncoder().encodeToString(raw)
        } finally {
            raw.fill(0)
        }

        assertFalse(pairing.phone.toString().contains(rawBase64))
        assertFalse(created.value.toString().contains(rawBase64))
        assertTrue(created.value.toString().contains("<redacted>"))
    }

    private fun pair(phoneId: String, tvId: String): PairedCandidates =
        pair(PairingIdentityKey.generate(phoneId), PairingIdentityKey.generate(tvId))

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

    private fun mutateBase64Url(value: String): String {
        val bytes = Base64.getUrlDecoder().decode(value)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        return try {
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private data class PairedCandidates(
        val phone: DevicePairingCandidate,
        val tv: DevicePairingCandidate,
    )
}
