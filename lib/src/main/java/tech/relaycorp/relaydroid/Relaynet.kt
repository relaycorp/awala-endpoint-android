package tech.relaycorp.relaydroid

import android.content.Context
import tech.relaycorp.relaydroid.persistence.SharedPreferencesPersistence

object Relaynet {
    const val POWEB_PORT = 13276
    const val GATEWAY_PACKAGE = "tech.relaycorp.gateway"
    const val GATEWAY_PRE_REGISTER_COMPONENT = "tech.relaycorp.gateway.background.endpoint.EndpointPreRegistrationService"
    const val GATEWAY_SYNC_COMPONENT = "tech.relaycorp.gateway.background.endpoint.GatewaySyncService"

    suspend fun setup(context: Context) {
        storage = StorageImpl(SharedPreferencesPersistence(context))
        gatewayClientImpl = GatewayClientImpl(context)
    }

    internal lateinit var storage: StorageImpl
    internal lateinit var gatewayClientImpl: GatewayClientI
}

internal val Storage = Relaynet.storage
val GatewayClient get() = Relaynet.gatewayClientImpl
