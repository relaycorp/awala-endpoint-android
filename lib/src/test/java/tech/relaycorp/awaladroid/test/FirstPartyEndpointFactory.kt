package tech.relaycorp.awaladroid.test

import tech.relaycorp.awaladroid.endpoint.FirstPartyEndpoint
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath

internal object FirstPartyEndpointFactory {
    fun build(): FirstPartyEndpoint = FirstPartyEndpoint(
        KeyPairSet.PRIVATE_ENDPOINT.private,
        PDACertPath.PRIVATE_ENDPOINT,
        listOf(PDACertPath.PRIVATE_GW),
        "frankfurt.relaycorp.cloud",
    )
}
