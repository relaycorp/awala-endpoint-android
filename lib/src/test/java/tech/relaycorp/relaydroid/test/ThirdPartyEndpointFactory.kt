package tech.relaycorp.relaydroid.test

import tech.relaycorp.relaydroid.endpoint.PrivateThirdPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.PublicThirdPartyEndpoint
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import tech.relaycorp.relaynet.testing.pki.PDACertPath

internal object ThirdPartyEndpointFactory {
    fun build(type: RecipientAddressType) =
        when (type) {
            RecipientAddressType.PUBLIC -> buildPublic()
            RecipientAddressType.PRIVATE -> buildPrivate()
        }

    fun buildPublic(): PublicThirdPartyEndpoint = PublicThirdPartyEndpoint(
        "example.org",
        PDACertPath.PDA
    )

    fun buildPrivate(): PrivateThirdPartyEndpoint = PrivateThirdPartyEndpoint(
        PDACertPath.PDA.subjectPrivateAddress,
        PDACertPath.PRIVATE_ENDPOINT,
        PDACertPath.PDA,
        listOf(PDACertPath.PRIVATE_GW)
    )
}
