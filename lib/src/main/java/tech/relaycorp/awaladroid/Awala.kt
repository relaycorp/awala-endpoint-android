package tech.relaycorp.awaladroid

import android.content.Context
import tech.relaycorp.awaladroid.background.ServiceInteractor
import tech.relaycorp.awaladroid.storage.StorageImpl
import tech.relaycorp.awaladroid.storage.persistence.EncryptedDiskPersistence

public object Awala {
    internal const val POWEB_PORT = 13276
    internal const val GATEWAY_PACKAGE =
        "tech.relaycorp.gateway"
    internal const val GATEWAY_PRE_REGISTER_ACTION =
        "tech.relaycorp.gateway.ENDPOINT_PRE_REGISTRATION"
    internal const val GATEWAY_PRE_REGISTER_COMPONENT =
        "tech.relaycorp.gateway.background.endpoint.EndpointPreRegistrationService"
    internal const val GATEWAY_SYNC_COMPONENT =
        "tech.relaycorp.gateway.background.endpoint.GatewaySyncService"
    internal const val GATEWAY_SYNC_ACTION =
        "tech.relaycorp.gateway.SYNC"

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
    internal var gatewayClientImpl: GatewayClientImpl? = null
}

/**
 * Private gateway client.
 */
public val GatewayClient: GatewayClientImpl
    get() = Awala.gatewayClientImpl ?: throw SetupPendingException()

internal val Storage get() = Awala.storage

public class SetupPendingException :
    AwaladroidException("Call Awala.setup before using the GatewayClient")
