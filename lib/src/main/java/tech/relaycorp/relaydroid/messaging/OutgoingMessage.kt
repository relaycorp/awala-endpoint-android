package tech.relaycorp.relaydroid.messaging

import tech.relaycorp.relaydroid.endpoint.FirstPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.PrivateThirdPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.PublicThirdPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.ThirdPartyEndpoint
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.messages.payloads.ServiceMessage
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import java.time.Duration
import java.time.ZonedDateTime

/**
 * An outgoing service message.
 *
 * @property senderEndpoint The first-party endpoint that created the message.
 * @property recipientEndpoint The third-party endpoint that should receive the message.
 * @property parcelExpiryDate The expiry date of the parcel.
 * @property parcelId The parcel id.
 */
public class OutgoingMessage
private constructor(
    public val senderEndpoint: FirstPartyEndpoint,
    public val recipientEndpoint: ThirdPartyEndpoint,
    public val parcelExpiryDate: ZonedDateTime = maxExpiryDate(),
    public val parcelId: ParcelId,
    internal val parcelCreationDate: ZonedDateTime = ZonedDateTime.now()
) : Message() {

    internal lateinit var parcel: Parcel
        private set

    internal val ttl get() = Duration.between(parcelCreationDate, parcelExpiryDate).seconds.toInt()

    public companion object {
        internal fun maxExpiryDate() = ZonedDateTime.now().plusDays(30)

        /**
         * Create an outgoing service message (but don't send it).
         *
         * @param type The type of the message (e.g., "application/vnd.relaynet.ping-v1.ping").
         * @param content The contents of the service message.
         * @param senderEndpoint The endpoint used to send the message.
         * @param recipientEndpoint The endpoint that will receive the message.
         * @param parcelExpiryDate The date when the parcel should expire.
         * @param parcelId The id of the parcel.
         */
        public suspend fun build(
            type: String,
            content: ByteArray,
            senderEndpoint: FirstPartyEndpoint,
            recipientEndpoint: ThirdPartyEndpoint,
            parcelExpiryDate: ZonedDateTime = maxExpiryDate(),
            parcelId: ParcelId = ParcelId.generate()
        ): OutgoingMessage {
            val message = OutgoingMessage(
                senderEndpoint,
                recipientEndpoint,
                parcelExpiryDate,
                parcelId
            )
            message.parcel = message.buildParcel(type, content)
            return message
        }
    }

    private suspend fun buildParcel(
        serviceMessageType: String,
        serviceMessageContent: ByteArray
    ): Parcel {
        val serviceMessage = ServiceMessage(serviceMessageType, serviceMessageContent)
        return Parcel(
            recipientAddress = recipientEndpoint.address,
            payload = serviceMessage.encrypt(recipientEndpoint.identityCertificate),
            senderCertificate = getSenderCertificate(),
            messageId = parcelId.value,
            creationDate = parcelCreationDate,
            ttl = ttl,
            senderCertificateChain = getSenderCertificateChain()
        )
    }

    private fun getSenderCertificate(): Certificate =
        when (recipientEndpoint) {
            is PublicThirdPartyEndpoint -> getSelfSignedSenderCertificate()
            is PrivateThirdPartyEndpoint -> recipientEndpoint.pda
        }

    private fun getSelfSignedSenderCertificate(): Certificate =
        issueEndpointCertificate(
            senderEndpoint.keyPair.public,
            senderEndpoint.keyPair.private,
            validityStartDate = parcelCreationDate,
            validityEndDate = parcelExpiryDate
        )

    private fun getSenderCertificateChain(): Set<Certificate> =
        when (recipientEndpoint) {
            is PublicThirdPartyEndpoint -> emptySet()
            is PrivateThirdPartyEndpoint -> recipientEndpoint.pdaChain.toSet()
        }
}
