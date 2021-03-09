package tech.relaycorp.relaydroid.test

import tech.relaycorp.relaydroid.endpoint.PrivateThirdPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.PublicThirdPartyEndpoint
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import java.util.UUID

internal object ThirdPartyEndpointFactory {
    fun build(type: RecipientAddressType) =
        when (type) {
            RecipientAddressType.PUBLIC -> buildPublic()
            RecipientAddressType.PRIVATE -> buildPrivate()
        }

    fun buildPublic(): PublicThirdPartyEndpoint = PublicThirdPartyEndpoint(
        "example.org",
        UUID.randomUUID().toString(),
        PDACertPath.PUBLIC_GW
    )

    fun buildPrivate(): PrivateThirdPartyEndpoint = PrivateThirdPartyEndpoint(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        PDACertPath.PRIVATE_ENDPOINT,
        PDACertPath.PRIVATE_ENDPOINT
    )
}
