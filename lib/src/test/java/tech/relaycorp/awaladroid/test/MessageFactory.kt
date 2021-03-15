package tech.relaycorp.awaladroid.test

import tech.relaycorp.awaladroid.messaging.IncomingMessage
import tech.relaycorp.awaladroid.messaging.OutgoingMessage
import tech.relaycorp.relaynet.messages.payloads.ServiceMessage
import tech.relaycorp.relaynet.ramf.RecipientAddressType

internal object MessageFactory {
    val serviceMessage = ServiceMessage("application/foo", "the content".toByteArray())

    suspend fun buildOutgoing(recipientType: RecipientAddressType) = OutgoingMessage.build(
        serviceMessage.type,
        serviceMessage.content,
        senderEndpoint = FirstPartyEndpointFactory.build(),
        recipientEndpoint = ThirdPartyEndpointFactory.build(recipientType)
    )

    fun buildIncoming() = IncomingMessage(
        type = serviceMessage.type,
        content = serviceMessage.content,
        senderEndpoint = ThirdPartyEndpointFactory.buildPublic(),
        recipientEndpoint = FirstPartyEndpointFactory.build()
    ) {}
}
