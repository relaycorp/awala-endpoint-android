package tech.relaycorp.awaladroid.test

import tech.relaycorp.awaladroid.endpoint.PrivateThirdPartyEndpoint
import tech.relaycorp.awaladroid.endpoint.PublicThirdPartyEndpoint
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath

internal object ThirdPartyEndpointFactory {
    fun build(type: RecipientAddressType) =
        when (type) {
            RecipientAddressType.PUBLIC -> buildPublic()
            RecipientAddressType.PRIVATE -> buildPrivate()
        }

    fun buildPublic(): PublicThirdPartyEndpoint = PublicThirdPartyEndpoint(
        "example.org",
        KeyPairSet.PDA_GRANTEE.public
    )

    fun buildPrivate(): PrivateThirdPartyEndpoint = PrivateThirdPartyEndpoint(
        PDACertPath.PDA.subjectPrivateAddress,
        KeyPairSet.PRIVATE_ENDPOINT.public,
        PDACertPath.PDA,
        listOf(PDACertPath.PRIVATE_GW)
    )
}
