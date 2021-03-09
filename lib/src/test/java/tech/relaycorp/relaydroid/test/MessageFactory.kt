package tech.relaycorp.relaydroid.test

import tech.relaycorp.relaydroid.messaging.IncomingMessage
import tech.relaycorp.relaydroid.messaging.MessageId
import tech.relaycorp.relaydroid.messaging.OutgoingMessage
import tech.relaycorp.relaynet.messages.payloads.ServiceMessage
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import java.time.ZonedDateTime
import java.util.UUID

internal object MessageFactory {
    val serviceMessage = ServiceMessage("application/foo", "the content".toByteArray())

    suspend fun buildOutgoing(recipientType: RecipientAddressType) = OutgoingMessage.build(
        serviceMessage.type,
        serviceMessage.content,
        senderEndpoint = FirstPartyEndpointFactory.build(),
        recipientEndpoint = ThirdPartyEndpointFactory.build(recipientType)
    )

    fun buildIncoming() = IncomingMessage(
        id = MessageId(UUID.randomUUID().toString()),
        type = serviceMessage.type,
        content = serviceMessage.content,
        senderEndpoint = ThirdPartyEndpointFactory.buildPublic(),
        recipientEndpoint = FirstPartyEndpointFactory.build(),
        creationDate = ZonedDateTime.now(),
        expiryDate = ZonedDateTime.now().plusDays(1),
        ack = {}
    )
}
