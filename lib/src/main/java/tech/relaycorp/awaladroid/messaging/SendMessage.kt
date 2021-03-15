package tech.relaycorp.awaladroid.messaging

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.relaycorp.awaladroid.GatewayException
import tech.relaycorp.awaladroid.GatewayProtocolException
import tech.relaycorp.awaladroid.Awala
import tech.relaycorp.poweb.PoWebClient
import tech.relaycorp.relaynet.bindings.pdc.ClientBindingException
import tech.relaycorp.relaynet.bindings.pdc.PDCClient
import tech.relaycorp.relaynet.bindings.pdc.RejectedParcelException
import tech.relaycorp.relaynet.bindings.pdc.ServerException
import tech.relaycorp.relaynet.bindings.pdc.Signer

internal class SendMessage(
    private val pdcClientBuilder: () -> PDCClient = { PoWebClient.initLocal(Awala.POWEB_PORT) },
    private val coroutineContext: CoroutineContext = Dispatchers.IO
) {

    @Throws(
        SendMessageException::class,
        RejectedMessageException::class,
        GatewayProtocolException::class
    )
    suspend fun send(message: OutgoingMessage) {
        withContext(coroutineContext) {
            val senderPrivateKey = message.senderEndpoint.keyPair.private

            return@withContext try {
                pdcClientBuilder().use {
                    it.deliverParcel(
                        message.parcel.serialize(senderPrivateKey),
                        Signer(
                            message.senderEndpoint.identityCertificate,
                            senderPrivateKey
                        )
                    )
                }
            } catch (e: ServerException) {
                throw SendMessageException("Server error", e)
            } catch (e: ClientBindingException) {
                throw GatewayProtocolException("Client error", e)
            } catch (e: RejectedParcelException) {
                throw RejectedMessageException("Parcel rejected by server", e)
            }
        }
    }
}

/**
 * The private gateway failed to process the outgoing message.
 *
 * This is most likely to be a bug in the private gateway. You should retry later.
 */
public class SendMessageException(message: String, cause: Throwable? = null) :
    GatewayException(message, cause)

/**
 * The private gateway refused to accept an outgoing message.
 *
 * It could be that the first-party endpoint certificate isn't valid anymore or the message
 * already expired, for example.
 *
 * Retrying won't make any difference.
 */
public class RejectedMessageException(message: String, cause: Throwable? = null) :
    GatewayException(message, cause)
