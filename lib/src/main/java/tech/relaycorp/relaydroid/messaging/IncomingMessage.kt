package tech.relaycorp.relaydroid.messaging

import tech.relaycorp.relaydroid.endpoint.FirstPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.ThirdPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.UnknownFirstPartyEndpointException
import tech.relaycorp.relaydroid.endpoint.UnknownThirdPartyEndpointException
import tech.relaycorp.relaydroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.messages.Parcel
import java.time.ZonedDateTime
import tech.relaycorp.relaynet.messages.InvalidMessageException
import tech.relaycorp.relaynet.wrappers.cms.EnvelopedDataException

public class IncomingMessage internal constructor(
    id: MessageId,
    public val type: String,
    public val content: ByteArray,
    public val senderEndpoint: ThirdPartyEndpoint,
    public val recipientEndpoint: FirstPartyEndpoint,
    creationDate: ZonedDateTime,
    expiryDate: ZonedDateTime,
    public val ack: suspend () -> Unit
) : Message(
    id, senderEndpoint, recipientEndpoint, creationDate, expiryDate
) {

    internal companion object {
        @Throws(
            UnknownFirstPartyEndpointException::class,
            UnknownThirdPartyEndpointException::class,
            PersistenceException::class,
            EnvelopedDataException::class,
            InvalidMessageException::class
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

            val serviceMessage = parcel.unwrapPayload(recipientEndpoint.keyPair.private)
            return IncomingMessage(
                id = MessageId(parcel.id),
                type = serviceMessage.type,
                content = serviceMessage.content,
                senderEndpoint = sender,
                recipientEndpoint = recipientEndpoint,
                creationDate = parcel.creationDate,
                expiryDate = parcel.expiryDate,
                ack = ack
            )
        }
    }
}
