package tech.relaycorp.relaydroid.test

import tech.relaycorp.relaydroid.FirstPartyEndpoint
import tech.relaycorp.relaynet.testing.pki.KeyPairSet

object FirstPartyEndpointFactory {
    fun build() = FirstPartyEndpoint(
        KeyPairSet.PRIVATE_ENDPOINT,
        ParcelDeliveryCertificates.PRIVATE_ENDPOINT,
        ParcelDeliveryCertificates.PRIVATE_GW
    )
}
