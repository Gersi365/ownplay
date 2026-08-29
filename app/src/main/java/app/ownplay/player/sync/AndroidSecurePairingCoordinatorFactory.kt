package app.ownplay.player.sync

import android.content.Context
import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.persistence.sync.RoomPairingDeviceIdStore

/** Production wiring for the transport-agnostic secure pairing coordinator. */
internal fun createAndroidSecurePairingCoordinator(
    context: Context,
    database: OwnPlayDatabase,
): SecurePairingCoordinator {
    val applicationContext = context.applicationContext
    return SecurePairingCoordinator(
        deviceIdStore = RoomPairingDeviceIdStore(database),
        secureStateFactory = { deviceId ->
            val androidState = AndroidSecureDevicePairingState(applicationContext, deviceId)
            object : SecureDevicePairingState {
                override val localDeviceId: String = androidState.localDeviceId
                override val identityKey: PairingIdentityKey
                    get() = androidState.identityKey
                override val keyRing: ProvisionedPortableSourceSecretKeyRing
                    get() = androidState.keyRing
            }
        },
    )
}
