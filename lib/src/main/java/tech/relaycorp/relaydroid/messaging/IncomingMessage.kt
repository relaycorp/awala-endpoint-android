package tech.relaycorp.relaydroid.messaging

import tech.relaycorp.relaydroid.FirstPartyEndpoint
import tech.relaycorp.relaydroid.PrivateThirdPartyEndpoint
import tech.relaycorp.relaydroid.ThirdPartyEndpoint
import tech.relaycorp.relaynet.messages.Parcel
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
) {

    internal companion object {
        internal suspend fun build(parcel: Parcel, ack: suspend () -> Unit) =
            IncomingMessage(
                id = MessageId(parcel.id),
                payload = parcel.payload,
                senderEndpoint = PrivateThirdPartyEndpoint(parcel.senderCertificate.subjectPrivateAddress),
                recipientEndpoint = FirstPartyEndpoint.load(parcel.recipientAddress)!!,
                creationDate = parcel.creationDate,
                expiryDate = parcel.expiryDate,
                ack = ack
            )
    }
}
