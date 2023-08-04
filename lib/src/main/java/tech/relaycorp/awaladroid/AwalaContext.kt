package tech.relaycorp.awaladroid

import tech.relaycorp.awaladroid.endpoint.ChannelManager
import tech.relaycorp.awaladroid.endpoint.HandleGatewayCertificateChange
import tech.relaycorp.awaladroid.storage.StorageImpl
import tech.relaycorp.relaynet.keystores.CertificateStore
import tech.relaycorp.relaynet.keystores.PrivateKeyStore
import tech.relaycorp.relaynet.keystores.SessionPublicKeyStore
import tech.relaycorp.relaynet.nodes.EndpointManager

internal data class AwalaContext(
    val storage: StorageImpl,
    val gatewayClient: GatewayClientImpl,
    val endpointManager: EndpointManager,
    val channelManager: ChannelManager,
    val privateKeyStore: PrivateKeyStore,
    val sessionPublicKeyStore: SessionPublicKeyStore,
    val certificateStore: CertificateStore,
    val handleGatewayCertificateChange: HandleGatewayCertificateChange,
)
