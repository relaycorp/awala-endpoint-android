package tech.relaycorp.relaydroid.test

import tech.relaycorp.relaydroid.PrivateThirdPartyEndpoint
import tech.relaycorp.relaydroid.PublicThirdPartyEndpoint
import tech.relaycorp.relaydroid.messaging.OutgoingMessage
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import java.util.UUID
import kotlin.random.Random

internal object MessageFactory {
    suspend fun buildOutgoing(recipientType: RecipientAddressType) = OutgoingMessage.build(
        Random.Default.nextBytes(10),
        senderEndpoint = FirstPartyEndpointFactory.build(),
        recipientEndpoint = when (recipientType) {
            RecipientAddressType.PUBLIC -> PublicThirdPartyEndpoint("http://example.org")
            RecipientAddressType.PRIVATE -> PrivateThirdPartyEndpoint(UUID.randomUUID().toString())
        }
    )
}
