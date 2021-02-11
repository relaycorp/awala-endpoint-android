package tech.relaycorp.relaydroid

import android.content.Context
import tech.relaycorp.relaydroid.persistence.EncryptedDiskPersistence

public object Relaynet {
    internal const val POWEB_PORT = 13276
    internal const val GATEWAY_PACKAGE = "tech.relaycorp.gateway"
    internal const val GATEWAY_PRE_REGISTER_COMPONENT = "tech.relaycorp.gateway.background.endpoint.EndpointPreRegistrationService"
    internal const val GATEWAY_SYNC_COMPONENT = "tech.relaycorp.gateway.background.endpoint.GatewaySyncService"

    public suspend fun setup(context: Context) {
        storage = StorageImpl(EncryptedDiskPersistence(context))
        gatewayClientImpl = GatewayClientImpl(context)
    }

    internal lateinit var storage: StorageImpl
    internal lateinit var gatewayClientImpl: GatewayClientI
}

internal val Storage get() = Relaynet.storage
public val GatewayClient: GatewayClientI get() = Relaynet.gatewayClientImpl
