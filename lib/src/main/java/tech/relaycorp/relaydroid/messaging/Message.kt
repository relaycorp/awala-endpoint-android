package tech.relaycorp.relaydroid.messaging

import tech.relaycorp.relaydroid.endpoint.Endpoint
import java.time.Duration
import java.time.ZonedDateTime

public abstract class Message(
    public val id: MessageId,
    senderEndpoint: Endpoint,
    recipientEndpoint: Endpoint,
    public val creationDate: ZonedDateTime = ZonedDateTime.now(),
    public val expiryDate: ZonedDateTime = maxExpiryDate()
) {

    internal val ttl get() = Duration.between(creationDate, expiryDate).seconds.toInt()

    internal companion object {
        internal fun maxExpiryDate() = ZonedDateTime.now().plusDays(30)
    }
}
