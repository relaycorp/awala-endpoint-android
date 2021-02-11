package tech.relaycorp.relaydroid.messaging

import tech.relaycorp.relaydroid.Endpoint
import tech.relaycorp.relaydroid.FirstPartyEndpoint
import tech.relaycorp.relaydroid.RelaynetException
import tech.relaycorp.relaydroid.ThirdPartyEndpoint
import java.time.Duration
import java.time.ZonedDateTime

public abstract class Message(
    public val id: MessageId,
    public val message: ByteArray,
    senderEndpoint: Endpoint,
    receiverEndpoint: Endpoint,
    public val creationDate: ZonedDateTime = ZonedDateTime.now(),
    public val expirationDate: ZonedDateTime = maxExpirationDate()
) {

    internal val ttl get() = Duration.between(creationDate, expirationDate).seconds.toInt()

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
) {
    internal fun validate() {
        if (message.isEmpty()) {
            throw InvalidMessageException("Empty message")
        }
        if (creationDate > ZonedDateTime.now()) {
            throw InvalidMessageException("Creation date must be in the past")
        }
        if (creationDate >= expirationDate) {
            throw InvalidMessageException("Expiration date must be after creation date")
        }
        if (Duration.between(creationDate, expirationDate).toDays() > 30) {
            throw InvalidMessageException(
                "Expiration date cannot be longer than 30 days after creation date"
            )
        }
    }
}


public class InvalidMessageException(message: String) : RelaynetException(message)
