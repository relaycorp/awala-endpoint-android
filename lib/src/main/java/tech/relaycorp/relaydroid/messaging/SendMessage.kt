package tech.relaycorp.relaydroid.messaging

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.relaycorp.poweb.PoWebClient
import tech.relaycorp.relaydroid.PrivateThirdPartyEndpoint
import tech.relaycorp.relaydroid.PublicThirdPartyEndpoint
import tech.relaycorp.relaydroid.Relaynet
import tech.relaycorp.relaydroid.RelaynetException
import tech.relaycorp.relaydroid.common.Logging.logger
import tech.relaycorp.relaynet.bindings.pdc.Signer
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import java.util.logging.Level
import kotlin.coroutines.CoroutineContext

internal class SendMessage(
    private val poWebClientBuilder: () -> PoWebClient =
        { PoWebClient.initLocal(Relaynet.POWEB_PORT) },
    private val coroutineContext: CoroutineContext = Dispatchers.IO
) {

    suspend fun send(message: OutgoingMessage) {
        withContext(coroutineContext) {
            message.validate()

            val parcel = buildParcel(message)
            val senderPrivateKey = message.senderEndpoint.keyPair.private

            return@withContext try {
                poWebClientBuilder()
                    .deliverParcel(
                        parcel.serialize(senderPrivateKey),
                        Signer(
                            parcel.senderCertificate,
                            senderPrivateKey
                        )
                    )
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Error sending message", e)
                throw SendMessageException("Could not deliver message to gateway", e)
            }
        }
    }

    private suspend fun buildParcel(message: OutgoingMessage) = Parcel(
        recipientAddress = message.receiverEndpoint.address,
        payload = message.message,
        senderCertificate = getSenderCertificate(message),
        messageId = message.id.value,
        creationDate = message.creationDate,
        ttl = message.ttl,
        senderCertificateChain = getSenderCertificateChain(message)
    )

    private suspend fun getSenderCertificate(message: OutgoingMessage) =
        when (message.receiverEndpoint) {
            is PublicThirdPartyEndpoint ->
                getSelfSignedSenderCertificate(message)
            is PrivateThirdPartyEndpoint ->
                getParcelDeliveryAuthorization(message, message.receiverEndpoint)
        }

    private fun getSelfSignedSenderCertificate(message: OutgoingMessage): Certificate {
        return issueEndpointCertificate(
            message.senderEndpoint.keyPair.public,
            message.senderEndpoint.keyPair.private,
            validityStartDate = message.creationDate,
            validityEndDate = message.expirationDate
        )
    }

    private suspend fun getParcelDeliveryAuthorization(
        message: OutgoingMessage,
        receiverEndpoint: PrivateThirdPartyEndpoint
    ): Certificate {
        TODO("Not yet implemented")
    }

    private suspend fun getSenderCertificateChain(message: OutgoingMessage): Set<Certificate> =
        when (message.receiverEndpoint) {
            is PublicThirdPartyEndpoint -> emptySet<Certificate>()
            is PrivateThirdPartyEndpoint -> TODO("Not implemented yet")
        } + message.senderEndpoint.gatewayCertificate
}

public class UnauthorizedReceiverException(endpoint: PrivateThirdPartyEndpoint)
    : RelaynetException("Unauthorized receiver ${endpoint.address}")

public class SendMessageException(message: String, cause: Throwable?)
    : RelaynetException(message, cause)

