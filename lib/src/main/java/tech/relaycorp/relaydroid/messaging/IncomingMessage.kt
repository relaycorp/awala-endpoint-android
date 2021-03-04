package tech.relaycorp.relaydroid.messaging

import tech.relaycorp.relaydroid.endpoint.FirstPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.ThirdPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.UnknownFirstPartyEndpointException
import tech.relaycorp.relaydroid.endpoint.UnknownThirdPartyEndpointException
import tech.relaycorp.relaydroid.storage.persistence.PersistenceException
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
        @Throws(
            UnknownFirstPartyEndpointException::class,
            UnknownThirdPartyEndpointException::class,
            PersistenceException::class
        )
        internal suspend fun build(parcel: Parcel, ack: suspend () -> Unit): IncomingMessage {
            val recipientEndpoint = FirstPartyEndpoint.load(parcel.recipientAddress)
                ?: throw UnknownFirstPartyEndpointException(
                    "Unknown third party endpoint with address ${parcel.recipientAddress}"
                )

            val sender = ThirdPartyEndpoint.load(
                parcel.recipientAddress,
                parcel.senderCertificate.subjectPrivateAddress,
            ) ?: throw UnknownThirdPartyEndpointException(
                "Unknown third party endpoint with address " +
                    "${parcel.senderCertificate.subjectPrivateAddress} " +
                    "for first party endpoint ${parcel.recipientAddress}"
            )

            return IncomingMessage(
                id = MessageId(parcel.id),
                payload = parcel.payload,
                senderEndpoint = sender,
                recipientEndpoint = recipientEndpoint,
                creationDate = parcel.creationDate,
                expiryDate = parcel.expiryDate,
                ack = ack
            )
        }
    }
}
