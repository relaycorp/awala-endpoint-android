package tech.relaycorp.awaladroid.messaging

import tech.relaycorp.awaladroid.Awala
import tech.relaycorp.awaladroid.endpoint.FirstPartyEndpoint
import tech.relaycorp.awaladroid.endpoint.PrivateThirdPartyEndpoint
import tech.relaycorp.awaladroid.endpoint.PublicThirdPartyEndpoint
import tech.relaycorp.awaladroid.endpoint.ThirdPartyEndpoint
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.messages.payloads.ServiceMessage
import tech.relaycorp.relaynet.ramf.RAMFException
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
    public val parcelExpiryDate: ZonedDateTime,
    public val parcelId: ParcelId,
    internal val parcelCreationDate: ZonedDateTime,
) : Message() {

    internal lateinit var parcel: Parcel
        private set

    internal val ttl get() = Duration.between(parcelCreationDate, parcelExpiryDate).seconds.toInt()

    public companion object {
        private val CLOCK_DRIFT_OFFSET = Duration.ofMinutes(5)
        private val MAX_TTL = Duration.ofDays(180)

        private fun maxExpiryDate() = ZonedDateTime.now().plus(MAX_TTL).minus(CLOCK_DRIFT_OFFSET)

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
            parcelId: ParcelId = ParcelId.generate(),
        ): OutgoingMessage {
            val message = OutgoingMessage(
                senderEndpoint,
                recipientEndpoint,
                parcelExpiryDate,
                parcelId,
                ZonedDateTime.now().minus(CLOCK_DRIFT_OFFSET),
            )
            message.parcel = message.buildParcel(type, content)
            return message
        }
    }

    private suspend fun buildParcel(
        serviceMessageType: String,
        serviceMessageContent: ByteArray,
    ): Parcel {
        val serviceMessage = ServiceMessage(serviceMessageType, serviceMessageContent)
        val endpointManager = Awala.getContextOrThrow().endpointManager
        val payload = endpointManager.wrapMessagePayload(
            serviceMessage,
            recipientEndpoint.nodeId,
            senderEndpoint.nodeId,
        )
        val parcel = try {
            Parcel(
                recipient = recipientEndpoint.recipient,
                payload = payload,
                senderCertificate = getSenderCertificate(),
                messageId = parcelId.value,
                creationDate = parcelCreationDate,
                ttl = ttl,
                senderCertificateChain = getSenderCertificateChain(),
            )
        } catch (exc: RAMFException) {
            throw InvalidMessageException("Failed to create parcel", exc)
        }
        return parcel
    }

    private fun getSenderCertificate(): Certificate =
        when (recipientEndpoint) {
            is PublicThirdPartyEndpoint -> getSelfSignedSenderCertificate()
            is PrivateThirdPartyEndpoint -> recipientEndpoint.pda
        }

    private fun getSelfSignedSenderCertificate(): Certificate =
        issueEndpointCertificate(
            senderEndpoint.identityCertificate.subjectPublicKey,
            senderEndpoint.identityPrivateKey,
            validityStartDate = parcelCreationDate,
            validityEndDate = parcelExpiryDate,
        )

    private fun getSenderCertificateChain(): Set<Certificate> =
        when (recipientEndpoint) {
            is PublicThirdPartyEndpoint -> emptySet()
            is PrivateThirdPartyEndpoint -> recipientEndpoint.pdaChain.toSet()
        }
}
