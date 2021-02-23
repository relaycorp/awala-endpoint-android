package tech.relaycorp.relaydroid.messaging

import tech.relaycorp.relaydroid.FirstPartyEndpoint
import tech.relaycorp.relaydroid.PrivateThirdPartyEndpoint
import tech.relaycorp.relaydroid.PublicThirdPartyEndpoint
import tech.relaycorp.relaydroid.RelaynetException
import tech.relaycorp.relaydroid.ThirdPartyEndpoint
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.messages.InvalidMessageException
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.ramf.RAMFException
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import java.time.ZonedDateTime

public class OutgoingMessage
private constructor(
    payload: ByteArray,
    public val senderEndpoint: FirstPartyEndpoint,
    public val recipientEndpoint: ThirdPartyEndpoint,
    creationDate: ZonedDateTime = ZonedDateTime.now(),
    expirationDate: ZonedDateTime = maxExpirationDate(),
    id: MessageId = MessageId.generate()
) : Message(
    id, payload, senderEndpoint, recipientEndpoint, creationDate, expirationDate
) {

    internal lateinit var parcel: Parcel
        private set

    public companion object {
        public suspend fun build(
            payload: ByteArray,
            senderEndpoint: FirstPartyEndpoint,
            recipientEndpoint: ThirdPartyEndpoint,
            creationDate: ZonedDateTime = ZonedDateTime.now(),
            expirationDate: ZonedDateTime = maxExpirationDate(),
            id: MessageId = MessageId.generate()
        ): OutgoingMessage {
            val message = OutgoingMessage(
                payload, senderEndpoint, recipientEndpoint, creationDate, expirationDate, id
            )
            message.parcel = message.buildParcel()
            try {
                message.parcel.validate(null)
            } catch (exp: RAMFException) {
                throw InvalidMessageException("Invalid outgoing message", exp)
            }
            return message
        }
    }

    private suspend fun buildParcel() = Parcel(
        recipientAddress = recipientEndpoint.address,
        payload = payload,
        senderCertificate = getSenderCertificate(),
        messageId = id.value,
        creationDate = creationDate,
        ttl = ttl,
        senderCertificateChain = getSenderCertificateChain()
    )

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
            validityEndDate = expirationDate
        )
    }

    private suspend fun getParcelDeliveryAuthorization(recipientEndpoint: PrivateThirdPartyEndpoint)
        : Certificate {
        TODO("Not yet implemented")
    }

    private suspend fun getSenderCertificateChain(): Set<Certificate> =
        when (recipientEndpoint) {
            is PublicThirdPartyEndpoint -> emptySet<Certificate>()
            is PrivateThirdPartyEndpoint -> TODO("Not implemented yet")
        } + senderEndpoint.gatewayCertificate
}
