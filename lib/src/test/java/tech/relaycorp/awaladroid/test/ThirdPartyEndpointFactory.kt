package tech.relaycorp.awaladroid.test

import tech.relaycorp.awaladroid.endpoint.PrivateThirdPartyEndpoint
import tech.relaycorp.awaladroid.endpoint.PublicThirdPartyEndpoint
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath

internal object ThirdPartyEndpointFactory {
    fun buildPublic(): PublicThirdPartyEndpoint = PublicThirdPartyEndpoint(
        "example.org",
        KeyPairSet.PDA_GRANTEE.public
    )

    fun buildPrivate(): PrivateThirdPartyEndpoint = PrivateThirdPartyEndpoint(
        PDACertPath.PRIVATE_ENDPOINT.subjectId,
        KeyPairSet.PDA_GRANTEE.public,
        PDACertPath.PDA,
        listOf(PDACertPath.PRIVATE_GW)
    )
}
