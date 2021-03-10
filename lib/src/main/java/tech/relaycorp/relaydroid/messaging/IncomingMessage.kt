package tech.relaycorp.relaydroid.messaging

import tech.relaycorp.relaydroid.endpoint.FirstPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.ThirdPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.UnknownFirstPartyEndpointException
import tech.relaycorp.relaydroid.endpoint.UnknownThirdPartyEndpointException
import tech.relaycorp.relaydroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.messages.InvalidMessageException
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.wrappers.cms.EnvelopedDataException

/**
 * An incoming service message.
 *
 * @property type The type of the service message (e.g., "application/vnd.relaynet.ping-v1.ping").
 * @property content The contents of the service message.
 * @property senderEndpoint The third-party endpoint that created the message.
 * @property recipientEndpoint The first-party endpoint that should receive the message.
 * @property ack The function to call as soon as the message has been processed.
 */
public class IncomingMessage internal constructor(
    parcelId: ParcelId,
    public val type: String,
    public val content: ByteArray,
    public val senderEndpoint: ThirdPartyEndpoint,
    public val recipientEndpoint: FirstPartyEndpoint,
    public val ack: suspend () -> Unit
) : Message(parcelId) {

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
                parcelId = ParcelId(parcel.id),
                type = serviceMessage.type,
                content = serviceMessage.content,
                senderEndpoint = sender,
                recipientEndpoint = recipientEndpoint,
                ack = ack
            )
        }
    }
}
