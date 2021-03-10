package tech.relaycorp.relaydroid

import android.content.Context
import tech.relaycorp.relaydroid.background.ServiceInteractor
import tech.relaycorp.relaydroid.storage.StorageImpl
import tech.relaycorp.relaydroid.storage.persistence.EncryptedDiskPersistence

public object Relaynet {
    internal const val POWEB_PORT = 13276
    internal const val GATEWAY_PACKAGE = "tech.relaycorp.gateway"
    internal const val GATEWAY_PRE_REGISTER_COMPONENT =
        "tech.relaycorp.gateway.background.endpoint.EndpointPreRegistrationService"
    internal const val GATEWAY_SYNC_COMPONENT =
        "tech.relaycorp.gateway.background.endpoint.GatewaySyncService"

    /**
     * Set up the endpoint library.
     */
    public suspend fun setup(context: Context) {
        storage = StorageImpl(EncryptedDiskPersistence(context))
        gatewayClientImpl = GatewayClientImpl(
            serviceInteractorBuilder = { ServiceInteractor(context) }
        )
    }

    internal lateinit var storage: StorageImpl
    internal lateinit var gatewayClientImpl: GatewayClientImpl
}

/**
 * Private gateway client.
 */
public val GatewayClient: GatewayClientImpl get() = Relaynet.gatewayClientImpl

internal val Storage get() = Relaynet.storage
