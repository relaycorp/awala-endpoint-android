package tech.relaycorp.awaladroid.test

import tech.relaycorp.awaladroid.messaging.IncomingMessage
import tech.relaycorp.awaladroid.messaging.OutgoingMessage
import tech.relaycorp.relaynet.messages.payloads.ServiceMessage

internal object MessageFactory {
    val serviceMessage = ServiceMessage("application/foo", "the content".toByteArray())

    suspend fun buildOutgoing(channel: EndpointChannel) =
        OutgoingMessage.build(
            serviceMessage.type,
            serviceMessage.content,
            senderEndpoint = channel.firstPartyEndpoint,
            recipientEndpoint = channel.thirdPartyEndpoint,
        )

    fun buildIncoming() =
        IncomingMessage(
            type = serviceMessage.type,
            content = serviceMessage.content,
            senderEndpoint = ThirdPartyEndpointFactory.buildPublic(),
            recipientEndpoint = FirstPartyEndpointFactory.build(),
        ) {}
}
