package tech.relaycorp.relaydroid.test

import tech.relaycorp.relaydroid.FirstPartyEndpoint
import tech.relaycorp.relaydroid.PublicThirdPartyEndpoint
import tech.relaycorp.relaydroid.messaging.OutgoingMessage
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import kotlin.random.Random

internal object MessageFactory {
    fun buildOutgoing() = OutgoingMessage(
        Random.Default.nextBytes(10),
        senderEndpoint = FirstPartyEndpointFactory.build(),
        receiverEndpoint = PublicThirdPartyEndpoint("http://example.org")
    )
}
