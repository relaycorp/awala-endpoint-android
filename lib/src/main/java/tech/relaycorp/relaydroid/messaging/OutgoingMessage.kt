package tech.relaycorp.relaydroid.messaging

import java.time.Duration
import java.time.ZonedDateTime
import tech.relaycorp.relaydroid.endpoint.FirstPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.PrivateThirdPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.PublicThirdPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.ThirdPartyEndpoint
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.messages.payloads.ServiceMessage
import tech.relaycorp.relaynet.wrappers.x509.Certificate

public class OutgoingMessage
private constructor(
    public val senderEndpoint: FirstPartyEndpoint,
    public val recipientEndpoint: ThirdPartyEndpoint,
    public val expiryDate: ZonedDateTime = maxExpiryDate(),
    id: MessageId,
    internal val creationDate: ZonedDateTime = ZonedDateTime.now()
) : Message(id) {

    internal lateinit var parcel: Parcel
        private set

    internal val ttl get() = Duration.between(creationDate, expiryDate).seconds.toInt()

    public companion object {
        internal fun maxExpiryDate() = ZonedDateTime.now().plusDays(30)

        public suspend fun build(
            type: String,
            content: ByteArray,
            senderEndpoint: FirstPartyEndpoint,
            recipientEndpoint: ThirdPartyEndpoint,
            expiryDate: ZonedDateTime = maxExpiryDate(),
            id: MessageId = MessageId.generate()
        ): OutgoingMessage {
            val message = OutgoingMessage(senderEndpoint, recipientEndpoint, expiryDate, id)
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
            messageId = id.value,
            creationDate = creationDate,
            ttl = ttl,
            senderCertificateChain = getSenderCertificateChain()
        )
    }

    private suspend fun getSenderCertificate() =
        when (recipientEndpoint) {
            is PublicThirdPartyEndpoint ->
                getSelfSignedSenderCertificate()
            is PrivateThirdPartyEndpoint ->
                getParcelDeliveryAuthorization(recipientEndpoint)
        }

    private fun getSelfSignedSenderCertificate(): Certificate {
        return issueEndpointCertificate(
            senderEndpoint.keyPair.public,
            senderEndpoint.keyPair.private,
            validityStartDate = creationDate,
            validityEndDate = expiryDate
        )
    }

    private suspend fun getParcelDeliveryAuthorization(
        recipientEndpoint: PrivateThirdPartyEndpoint
    ): Certificate {
        TODO("Not yet implemented")
    }

    private suspend fun getSenderCertificateChain(): Set<Certificate> =
        when (recipientEndpoint) {
            is PublicThirdPartyEndpoint -> emptySet()
            is PrivateThirdPartyEndpoint -> TODO("Include senderEndpoint.gatewayCertificate")
        }
}
