package tech.relaycorp.relaydroid.messaging

import tech.relaycorp.relaydroid.FirstPartyEndpoint
import tech.relaycorp.relaydroid.ThirdPartyEndpoint
import java.time.ZonedDateTime

public class IncomingMessage internal constructor(
    id: MessageId,
    payload: ByteArray,
    public val senderEndpoint: ThirdPartyEndpoint,
    public val recipientEndpoint: FirstPartyEndpoint,
    creationDate: ZonedDateTime,
    expiryDate: ZonedDateTime,
    public val ack: suspend () -> Unit
) : Message(
    id, payload, senderEndpoint, recipientEndpoint, creationDate, expiryDate
)
