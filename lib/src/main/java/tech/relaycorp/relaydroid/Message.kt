package tech.relaycorp.relaydroid

import java.time.ZonedDateTime
import java.util.*

abstract class Message(
    val id: MessageId,
    val message: ByteArray,
    senderEndpoint: Endpoint,
    receiverEndpoint: Endpoint,
    val creationDate: ZonedDateTime = ZonedDateTime.now(),
    val expirationDate: ZonedDateTime = maxExpirationDate()
) {
    companion object {
        internal fun maxExpirationDate() = ZonedDateTime.now().plusDays(30)
    }
}

class IncomingMessage internal constructor(
    id: MessageId,
    message: ByteArray,
    val senderEndpoint: ThirdPartyEndpoint,
    val receiverEndpoint: FirstPartyEndpoint,
    creationDate: ZonedDateTime,
    expiryDate: ZonedDateTime,
    val ack: suspend () -> Unit
) : Message(
    id, message, senderEndpoint, receiverEndpoint, creationDate, expiryDate
)

class OutgoingMessage(
    message: ByteArray,
    val senderEndpoint: FirstPartyEndpoint,
    val receiverEndpoint: ThirdPartyEndpoint,
    creationDate: ZonedDateTime = ZonedDateTime.now(),
    expirationDate: ZonedDateTime = maxExpirationDate(),
    id: MessageId = MessageId.generate()
) : Message(
    id, message, senderEndpoint, receiverEndpoint, creationDate, expirationDate
)
