package app.ownplay.player.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurePairingCoordinatorTest {
    @Test
    fun phoneAndTvCompleteHandshakeAndProvisionSameGroupKey() = runBlocking {
        val phoneFixture = fixture("phone")
        val tvFixture = fixture("tv")

        val offer = phoneFixture.coordinator.beginPairing().successValue()
        assertSame(
            SecurePairingCallResult.Busy,
            phoneFixture.coordinator.beginPairing(),
        )
        assertEquals(
            SecurePairingStage.INITIATOR_AWAITING_ANSWER,
            phoneFixture.coordinator.activeSessionStatus()?.stage,
        )

        val answer = tvFixture.coordinator.answerPairingOffer(offer).successValue()
        val initiatorVerification =
            phoneFixture.coordinator.acceptPairingAnswer(answer).successValue()
        val responderVerification =
            tvFixture.coordinator.acceptPairingConfirmation(
                initiatorVerification.confirmation,
            ).successValue()

        assertEquals(
            initiatorVerification.verification,
            responderVerification.verification,
        )
        assertEquals(
            offer.sessionId,
            phoneFixture.coordinator.activeSessionStatus()?.sessionId,
        )
        assertEquals(
            offer.sessionId,
            tvFixture.coordinator.activeSessionStatus()?.sessionId,
        )

        val creation = phoneFixture.coordinator.createProvisioningPackage(
            initiatorVerification.verification,
        ).successValue()
        assertTrue(creation is SyncKeyPackageCreationResult.Created)
        creation as SyncKeyPackageCreationResult.Created

        val acceptance = tvFixture.coordinator.acceptProvisioningPackage(
            verified = responderVerification.verification,
            provision = creation.value,
        ).successValue()
        assertTrue(acceptance is SyncKeyPackageAcceptanceResult.Accepted)
        acceptance as SyncKeyPackageAcceptanceResult.Accepted

        assertEquals(creation.value.keyId, acceptance.keyId)
        assertEquals(
            phoneFixture.state.keyRing.currentKey().keyId,
            tvFixture.state.keyRing.currentKey().keyId,
        )
        assertNull(phoneFixture.coordinator.activeSessionStatus())
        assertNull(tvFixture.coordinator.activeSessionStatus())
        assertNotNull(phoneFixture.coordinator.trustedPeer("tv"))
        assertNotNull(tvFixture.coordinator.trustedPeer("phone"))
    }

    @Test
    fun invalidAnswerDoesNotDestroyInitiatorSession() = runBlocking {
        val phone = fixture("phone")
        val tv = fixture("tv")
        val offer = phone.coordinator.beginPairing().successValue()
        val answer = tv.coordinator.answerPairingOffer(offer).successValue()

        val invalid = phone.coordinator.acceptPairingAnswer(
            answer.copy(sessionId = "wrong-session"),
        )

        assertSame(SecurePairingCallResult.InvalidRemoteMessage, invalid)
        assertEquals(
            SecurePairingStage.INITIATOR_AWAITING_ANSWER,
            phone.coordinator.activeSessionStatus()?.stage,
        )

        val recovered = phone.coordinator.acceptPairingAnswer(answer)
        assertTrue(recovered is SecurePairingCallResult.Success)
        assertEquals(
            SecurePairingStage.INITIATOR_READY_TO_PROVISION,
            phone.coordinator.activeSessionStatus()?.stage,
        )
    }

    @Test
    fun cancelReleasesOnlyActiveSessionAndCanRestart() = runBlocking {
        val phone = fixture("phone")

        assertFalse(phone.coordinator.cancelActiveSession())
        phone.coordinator.beginPairing().successValue()
        assertTrue(phone.coordinator.cancelActiveSession())
        assertNull(phone.coordinator.activeSessionStatus())

        val second = phone.coordinator.beginPairing()
        assertTrue(second is SecurePairingCallResult.Success)
    }

    @Test
    fun revokingTrustedPeerCancelsActiveSessionAndRequiresRotation() = runBlocking {
        val phone = fixture("phone")
        val tv = fixture("tv")
        pairAndProvision(phone, tv)

        val offer = phone.coordinator.beginPairing().successValue()
        val answer = tv.coordinator.answerPairingOffer(offer).successValue()
        phone.coordinator.acceptPairingAnswer(answer).successValue()

        val revocation = phone.coordinator.revokePeer("tv")

        assertTrue(revocation.revoked)
        assertTrue(revocation.rotationRequired)
        assertNull(phone.coordinator.activeSessionStatus())
        assertEquals(true, phone.coordinator.trustedPeer("tv")?.revoked)
    }

    @Test
    fun durableDeviceIdIsResolvedOncePerCoordinator() = runBlocking {
        var lookups = 0
        val state = TestSecureState("phone")
        val coordinator = SecurePairingCoordinator(
            deviceIdStore = PairingDeviceIdStore {
                lookups += 1
                "phone"
            },
            secureStateFactory = { state },
        )

        assertEquals("phone", coordinator.localIdentity().deviceId)
        assertEquals("phone", coordinator.localIdentity().deviceId)
        coordinator.beginPairing().successValue()

        assertEquals(1, lookups)
    }

    private suspend fun pairAndProvision(
        provider: Fixture,
        recipient: Fixture,
    ) {
        val offer = provider.coordinator.beginPairing().successValue()
        val answer = recipient.coordinator.answerPairingOffer(offer).successValue()
        val providerVerification = provider.coordinator.acceptPairingAnswer(answer).successValue()
        val recipientVerification = recipient.coordinator.acceptPairingConfirmation(
            providerVerification.confirmation,
        ).successValue()
        val creation = provider.coordinator.createProvisioningPackage(
            providerVerification.verification,
        ).successValue() as SyncKeyPackageCreationResult.Created
        recipient.coordinator.acceptProvisioningPackage(
            recipientVerification.verification,
            creation.value,
        ).successValue()
    }

    private fun fixture(deviceId: String): Fixture {
        val state = TestSecureState(deviceId)
        return Fixture(
            state = state,
            coordinator = SecurePairingCoordinator(
                deviceIdStore = PairingDeviceIdStore { deviceId },
                secureStateFactory = { resolvedDeviceId ->
                    require(resolvedDeviceId == deviceId)
                    state
                },
            ),
        )
    }

    private data class Fixture(
        val state: TestSecureState,
        val coordinator: SecurePairingCoordinator,
    )

    private class TestSecureState(
        override val localDeviceId: String,
    ) : SecureDevicePairingState {
        override val identityKey: PairingIdentityKey = PairingIdentityKey.generate(localDeviceId)
        override val keyRing = ProvisionedPortableSourceSecretKeyRing(localDeviceId)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> SecurePairingCallResult<T>.successValue(): T {
        assertTrue("Expected Success but was $this", this is SecurePairingCallResult.Success)
        return (this as SecurePairingCallResult.Success<T>).value
    }
}
