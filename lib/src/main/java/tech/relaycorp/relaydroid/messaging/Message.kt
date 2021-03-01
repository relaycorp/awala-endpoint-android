package tech.relaycorp.relaydroid.messaging

import tech.relaycorp.relaydroid.Endpoint
import java.time.Duration
import java.time.ZonedDateTime

public abstract class Message(
    public val id: MessageId,
    public val payload: ByteArray,
    senderEndpoint: Endpoint,
    recipientEndpoint: Endpoint,
    public val creationDate: ZonedDateTime = ZonedDateTime.now(),
    public val expirationDate: ZonedDateTime = maxExpirationDate()
) {

    internal val ttl get() = Duration.between(creationDate, expirationDate).seconds.toInt()

    internal companion object {
        internal fun maxExpirationDate() = ZonedDateTime.now().plusDays(30)
    }
}
