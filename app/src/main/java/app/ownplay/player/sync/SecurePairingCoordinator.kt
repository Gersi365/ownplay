package app.ownplay.player.sync

import java.security.GeneralSecurityException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun interface PairingDeviceIdStore {
    suspend fun getOrCreateDeviceId(): String
}

internal interface SecureDevicePairingState {
    val localDeviceId: String
    val identityKey: PairingIdentityKey
    val keyRing: ProvisionedPortableSourceSecretKeyRing
}

internal sealed interface SecurePairingCallResult<out T> {
    data class Success<T>(val value: T) : SecurePairingCallResult<T>
    data object Busy : SecurePairingCallResult<Nothing>
    data object NoActiveSession : SecurePairingCallResult<Nothing>
    data object WrongStage : SecurePairingCallResult<Nothing>
    data object InvalidRemoteMessage : SecurePairingCallResult<Nothing>
}

internal enum class SecurePairingStage {
    INITIATOR_AWAITING_ANSWER,
    INITIATOR_READY_TO_PROVISION,
    RESPONDER_AWAITING_CONFIRMATION,
    RESPONDER_READY_TO_PROVISION,
}

internal data class SecurePairingSessionStatus(
    val sessionId: String,
    val peerDeviceId: String?,
    val stage: SecurePairingStage,
)

internal data class SecurePairingInitiatorVerification(
    val verification: DevicePairingVerification,
    val confirmation: DevicePairingConfirmation,
)

internal data class SecurePairingResponderVerification(
    val verification: DevicePairingVerification,
)

/**
 * Runtime owner for one secure device-pairing session.
 *
 * This class deliberately knows nothing about UI or transport. Callers pass pairing messages in and
 * out, while this coordinator serializes state transitions, owns ephemeral session material and
 * delegates durable identity/trust/key state to SecureDevicePairingState.
 */
internal class SecurePairingCoordinator(
    private val deviceIdStore: PairingDeviceIdStore,
    private val secureStateFactory: (String) -> SecureDevicePairingState,
) {
    private val mutex = Mutex()
    private var secureState: SecureDevicePairingState? = null
    private var activeSession: ActiveSession? = null

    suspend fun localIdentity(): PairingDeviceIdentity = mutex.withLock {
        stateLocked().identityKey.identity
    }

    suspend fun activeSessionStatus(): SecurePairingSessionStatus? = mutex.withLock {
        activeSession?.status()
    }

    suspend fun beginPairing(): SecurePairingCallResult<DevicePairingOffer> = mutex.withLock {
        if (activeSession != null) return@withLock SecurePairingCallResult.Busy
        val context = DevicePairingProtocol.createOffer(stateLocked().identityKey)
        activeSession = ActiveSession.InitiatorAwaitingAnswer(context)
        SecurePairingCallResult.Success(context.offer)
    }

    suspend fun answerPairingOffer(
        offer: DevicePairingOffer,
    ): SecurePairingCallResult<DevicePairingAnswer> = mutex.withLock {
        if (activeSession != null) return@withLock SecurePairingCallResult.Busy
        val context = try {
            DevicePairingProtocol.answerOffer(stateLocked().identityKey, offer)
        } catch (_: IllegalArgumentException) {
            return@withLock SecurePairingCallResult.InvalidRemoteMessage
        } catch (_: GeneralSecurityException) {
            return@withLock SecurePairingCallResult.InvalidRemoteMessage
        }
        activeSession = ActiveSession.ResponderAwaitingConfirmation(context)
        SecurePairingCallResult.Success(context.answer)
    }

    suspend fun acceptPairingAnswer(
        answer: DevicePairingAnswer,
    ): SecurePairingCallResult<SecurePairingInitiatorVerification> = mutex.withLock {
        val session = activeSession ?: return@withLock SecurePairingCallResult.NoActiveSession
        if (session !is ActiveSession.InitiatorAwaitingAnswer) {
            return@withLock SecurePairingCallResult.WrongStage
        }
        val result = try {
            DevicePairingProtocol.acceptAnswer(session.context, answer)
        } catch (_: IllegalArgumentException) {
            return@withLock SecurePairingCallResult.InvalidRemoteMessage
        } catch (_: GeneralSecurityException) {
            return@withLock SecurePairingCallResult.InvalidRemoteMessage
        }
        activeSession = ActiveSession.InitiatorReadyToProvision(result.candidate)
        SecurePairingCallResult.Success(
            SecurePairingInitiatorVerification(
                verification = result.candidate.verification,
                confirmation = result.confirmation,
            ),
        )
    }

    suspend fun acceptPairingConfirmation(
        confirmation: DevicePairingConfirmation,
    ): SecurePairingCallResult<SecurePairingResponderVerification> = mutex.withLock {
        val session = activeSession ?: return@withLock SecurePairingCallResult.NoActiveSession
        if (session !is ActiveSession.ResponderAwaitingConfirmation) {
            return@withLock SecurePairingCallResult.WrongStage
        }
        val candidate = try {
            DevicePairingProtocol.acceptConfirmation(session.context, confirmation)
        } catch (_: IllegalArgumentException) {
            return@withLock SecurePairingCallResult.InvalidRemoteMessage
        } catch (_: GeneralSecurityException) {
            return@withLock SecurePairingCallResult.InvalidRemoteMessage
        }
        activeSession = ActiveSession.ResponderReadyToProvision(candidate)
        SecurePairingCallResult.Success(
            SecurePairingResponderVerification(candidate.verification),
        )
    }

    suspend fun createProvisioningPackage(
        verified: DevicePairingVerification,
    ): SecurePairingCallResult<SyncKeyPackageCreationResult> = mutex.withLock {
        val session = activeSession ?: return@withLock SecurePairingCallResult.NoActiveSession
        val candidate = session.readyCandidate()
            ?: return@withLock SecurePairingCallResult.WrongStage
        val result = stateLocked().keyRing.createProvisioningPackage(candidate, verified)
        if (result is SyncKeyPackageCreationResult.Created) clearActiveSessionLocked()
        SecurePairingCallResult.Success(result)
    }

    suspend fun acceptProvisioningPackage(
        verified: DevicePairingVerification,
        provision: SyncKeyProvisioningPackage,
    ): SecurePairingCallResult<SyncKeyPackageAcceptanceResult> = mutex.withLock {
        val session = activeSession ?: return@withLock SecurePairingCallResult.NoActiveSession
        val candidate = session.readyCandidate()
            ?: return@withLock SecurePairingCallResult.WrongStage
        val result = stateLocked().keyRing.acceptProvisioningPackage(candidate, verified, provision)
        if (result is SyncKeyPackageAcceptanceResult.Accepted) clearActiveSessionLocked()
        SecurePairingCallResult.Success(result)
    }

    suspend fun cancelActiveSession(): Boolean = mutex.withLock {
        if (activeSession == null) return@withLock false
        clearActiveSessionLocked()
        true
    }

    suspend fun trustedPeer(deviceId: String): PairingPeerTrustState? = mutex.withLock {
        require(deviceId.isNotBlank())
        stateLocked().keyRing.trustedPeer(deviceId)
    }

    suspend fun revokePeer(deviceId: String): PairingPeerRevocationResult = mutex.withLock {
        require(deviceId.isNotBlank())
        if (activeSession?.peerDeviceId() == deviceId) clearActiveSessionLocked()
        stateLocked().keyRing.revokePeer(deviceId)
    }

    suspend fun rotateCurrentKey(): SyncKeyRotationResult = mutex.withLock {
        stateLocked().keyRing.rotateCurrentKey()
    }

    suspend fun retireKey(keyId: String): Boolean = mutex.withLock {
        require(keyId.isNotBlank())
        stateLocked().keyRing.retireKey(keyId)
    }

    private suspend fun stateLocked(): SecureDevicePairingState {
        secureState?.let { return it }
        val deviceId = deviceIdStore.getOrCreateDeviceId()
        val state = secureStateFactory(deviceId)
        require(state.localDeviceId == deviceId) {
            "Secure pairing state is bound to a different device id"
        }
        secureState = state
        return state
    }

    private fun clearActiveSessionLocked() {
        activeSession?.wipe()
        activeSession = null
    }

    private sealed interface ActiveSession {
        fun status(): SecurePairingSessionStatus
        fun peerDeviceId(): String?
        fun readyCandidate(): DevicePairingCandidate? = null
        fun wipe() = Unit

        data class InitiatorAwaitingAnswer(
            val context: DevicePairingInitiatorContext,
        ) : ActiveSession {
            override fun status() = SecurePairingSessionStatus(
                sessionId = context.offer.sessionId,
                peerDeviceId = null,
                stage = SecurePairingStage.INITIATOR_AWAITING_ANSWER,
            )

            override fun peerDeviceId(): String? = null
        }

        data class InitiatorReadyToProvision(
            val candidate: DevicePairingCandidate,
        ) : ActiveSession {
            override fun status() = SecurePairingSessionStatus(
                sessionId = candidate.transcriptHashBase64Url,
                peerDeviceId = candidate.peer.deviceId,
                stage = SecurePairingStage.INITIATOR_READY_TO_PROVISION,
            )

            override fun peerDeviceId(): String = candidate.peer.deviceId
            override fun readyCandidate(): DevicePairingCandidate = candidate
            override fun wipe() = candidate.sessionKey.wipe()
        }

        data class ResponderAwaitingConfirmation(
            val context: DevicePairingResponderContext,
        ) : ActiveSession {
            override fun status() = SecurePairingSessionStatus(
                sessionId = context.offer.sessionId,
                peerDeviceId = context.offer.initiator.deviceId,
                stage = SecurePairingStage.RESPONDER_AWAITING_CONFIRMATION,
            )

            override fun peerDeviceId(): String = context.offer.initiator.deviceId
        }

        data class ResponderReadyToProvision(
            val candidate: DevicePairingCandidate,
        ) : ActiveSession {
            override fun status() = SecurePairingSessionStatus(
                sessionId = candidate.transcriptHashBase64Url,
                peerDeviceId = candidate.peer.deviceId,
                stage = SecurePairingStage.RESPONDER_READY_TO_PROVISION,
            )

            override fun peerDeviceId(): String = candidate.peer.deviceId
            override fun readyCandidate(): DevicePairingCandidate = candidate
            override fun wipe() = candidate.sessionKey.wipe()
        }
    }
}
