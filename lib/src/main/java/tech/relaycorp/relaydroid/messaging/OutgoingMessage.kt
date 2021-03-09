package tech.relaycorp.relaydroid.messaging

import tech.relaycorp.relaydroid.endpoint.FirstPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.PrivateThirdPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.PublicThirdPartyEndpoint
import tech.relaycorp.relaydroid.endpoint.ThirdPartyEndpoint
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.messages.InvalidMessageException
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.messages.payloads.ServiceMessage
import tech.relaycorp.relaynet.ramf.RAMFException
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import java.time.ZonedDateTime

public class OutgoingMessage
private constructor(
    public val senderEndpoint: FirstPartyEndpoint,
    public val recipientEndpoint: ThirdPartyEndpoint,
    creationDate: ZonedDateTime = ZonedDateTime.now(),
    expiryDate: ZonedDateTime = maxExpiryDate(),
    id: MessageId = MessageId.generate()
) : Message(
    id, senderEndpoint, recipientEndpoint, creationDate, expiryDate
) {

    internal lateinit var parcel: Parcel
        private set

    public companion object {
        public suspend fun build(
            type: String,
            content: ByteArray,
            senderEndpoint: FirstPartyEndpoint,
            recipientEndpoint: ThirdPartyEndpoint,
            creationDate: ZonedDateTime = ZonedDateTime.now(),
            expiryDate: ZonedDateTime = maxExpiryDate(),
            id: MessageId = MessageId.generate()
        ): OutgoingMessage {
            val message = OutgoingMessage(
                senderEndpoint, recipientEndpoint, creationDate, expiryDate, id
            )
            message.parcel = message.buildParcel(type, content)
            try {
                message.parcel.validate(null)
            } catch (exp: RAMFException) {
                throw InvalidMessageException("Invalid outgoing message", exp)
            }
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

    private suspend fun getParcelDeliveryAuthorization(recipientEndpoint: PrivateThirdPartyEndpoint)
        : Certificate {
        TODO("Not yet implemented")
    }

    private suspend fun getSenderCertificateChain(): Set<Certificate> =
        when (recipientEndpoint) {
            is PublicThirdPartyEndpoint -> emptySet()
            is PrivateThirdPartyEndpoint -> TODO("Not implemented yet") // + senderEndpoint.gatewayCertificate
        }
}
