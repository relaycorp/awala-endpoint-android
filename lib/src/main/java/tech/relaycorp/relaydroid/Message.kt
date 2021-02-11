package tech.relaycorp.relaydroid

import java.time.ZonedDateTime
import java.util.*

public abstract class Message(
    public val id: MessageId,
    public val message: ByteArray,
    senderEndpoint: Endpoint,
    receiverEndpoint: Endpoint,
    public val creationDate: ZonedDateTime = ZonedDateTime.now(),
    public val expirationDate: ZonedDateTime = maxExpirationDate()
) {
    internal companion object {
        internal fun maxExpirationDate() = ZonedDateTime.now().plusDays(30)
    }
}

public class IncomingMessage internal constructor(
    id: MessageId,
    message: ByteArray,
    public val senderEndpoint: ThirdPartyEndpoint,
    public val receiverEndpoint: FirstPartyEndpoint,
    creationDate: ZonedDateTime,
    expiryDate: ZonedDateTime,
    public val ack: suspend () -> Unit
) : Message(
    id, message, senderEndpoint, receiverEndpoint, creationDate, expiryDate
)

public class OutgoingMessage(
    message: ByteArray,
    public val senderEndpoint: FirstPartyEndpoint,
    public val receiverEndpoint: ThirdPartyEndpoint,
    creationDate: ZonedDateTime = ZonedDateTime.now(),
    expirationDate: ZonedDateTime = maxExpirationDate(),
    id: MessageId = MessageId.generate()
) : Message(
    id, message, senderEndpoint, receiverEndpoint, creationDate, expirationDate
)
