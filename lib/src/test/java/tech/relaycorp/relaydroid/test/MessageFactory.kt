package tech.relaycorp.relaydroid.test

import tech.relaycorp.relaydroid.PrivateThirdPartyEndpoint
import tech.relaycorp.relaydroid.PublicThirdPartyEndpoint
import tech.relaycorp.relaydroid.messaging.IncomingMessage
import tech.relaycorp.relaydroid.messaging.MessageId
import tech.relaycorp.relaydroid.messaging.OutgoingMessage
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import java.time.ZonedDateTime
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

    fun buildIncoming() = IncomingMessage(
        id = MessageId(UUID.randomUUID().toString()),
        payload = Random.nextBytes(10),
        senderEndpoint = PublicThirdPartyEndpoint("http://example.org"),
        recipientEndpoint = FirstPartyEndpointFactory.build(),
        creationDate = ZonedDateTime.now(),
        expiryDate = ZonedDateTime.now().plusDays(1),
        ack = {}
    )
}
