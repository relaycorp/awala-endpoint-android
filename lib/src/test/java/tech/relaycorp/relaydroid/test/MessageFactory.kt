package tech.relaycorp.relaydroid.test

import tech.relaycorp.relaydroid.endpoint.PrivateThirdPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.PublicThirdPartyEndpoint
import tech.relaycorp.relaydroid.messaging.IncomingMessage
import tech.relaycorp.relaydroid.messaging.MessageId
import tech.relaycorp.relaydroid.messaging.OutgoingMessage
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.random.Random

internal object MessageFactory {
    suspend fun buildOutgoing(recipientType: RecipientAddressType) = OutgoingMessage.build(
        Random.Default.nextBytes(10),
        senderEndpoint = FirstPartyEndpointFactory.build(),
        recipientEndpoint = when (recipientType) {
            RecipientAddressType.PUBLIC -> PublicThirdPartyEndpoint(
                "http://example.org",
                PDACertPath.PUBLIC_GW
            )
            RecipientAddressType.PRIVATE -> PrivateThirdPartyEndpoint(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                PDACertPath.PRIVATE_ENDPOINT,
                PDACertPath.PRIVATE_ENDPOINT
            )
        }
    )

    fun buildIncoming() = IncomingMessage(
        id = MessageId(UUID.randomUUID().toString()),
        payload = Random.nextBytes(10),
        senderEndpoint = PublicThirdPartyEndpoint("example.org", PDACertPath.PUBLIC_GW),
        recipientEndpoint = FirstPartyEndpointFactory.build(),
        creationDate = ZonedDateTime.now(),
        expiryDate = ZonedDateTime.now().plusDays(1),
        ack = {}
    )
}
