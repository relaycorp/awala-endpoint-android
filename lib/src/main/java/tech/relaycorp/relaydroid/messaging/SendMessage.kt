package tech.relaycorp.relaydroid.messaging

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.relaycorp.poweb.PoWebClient
import tech.relaycorp.relaydroid.GatewayException
import tech.relaycorp.relaydroid.GatewayProtocolException
import tech.relaycorp.relaydroid.Relaynet
import tech.relaycorp.relaynet.bindings.pdc.ClientBindingException
import tech.relaycorp.relaynet.bindings.pdc.PDCClient
import tech.relaycorp.relaynet.bindings.pdc.RejectedParcelException
import tech.relaycorp.relaynet.bindings.pdc.ServerException
import tech.relaycorp.relaynet.bindings.pdc.Signer
import kotlin.coroutines.CoroutineContext

internal class SendMessage(
    private val pdcClientBuilder: () -> PDCClient = { PoWebClient.initLocal(Relaynet.POWEB_PORT) },
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

public class SendMessageException(message: String, cause: Throwable? = null)
    : GatewayException(message, cause)

public class RejectedMessageException(message: String, cause: Throwable? = null)
    : GatewayException(message, cause)
