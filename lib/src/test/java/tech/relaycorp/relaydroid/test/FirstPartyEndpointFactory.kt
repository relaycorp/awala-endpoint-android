package tech.relaycorp.relaydroid.test

import tech.relaycorp.relaydroid.FirstPartyEndpoint
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath

object FirstPartyEndpointFactory {
    fun build() = FirstPartyEndpoint(
        KeyPairSet.PRIVATE_ENDPOINT,
        PDACertPath.PRIVATE_ENDPOINT,
        PDACertPath.PRIVATE_GW
    )
}
