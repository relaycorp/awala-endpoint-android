package tech.relaycorp.relaydroid.messaging

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.relaycorp.poweb.PoWebClient
import tech.relaycorp.relaydroid.GatewayRelaynetException
import tech.relaycorp.relaydroid.Relaynet
import tech.relaycorp.relaydroid.RelaynetException
import tech.relaycorp.relaydroid.common.Logging.logger
import tech.relaycorp.relaynet.bindings.pdc.ClientBindingException
import tech.relaycorp.relaynet.bindings.pdc.PDCClient
import tech.relaycorp.relaynet.bindings.pdc.RejectedParcelException
import tech.relaycorp.relaynet.bindings.pdc.ServerException
import tech.relaycorp.relaynet.bindings.pdc.Signer
import java.util.logging.Level
import kotlin.coroutines.CoroutineContext

internal class SendMessage(
    private val pdcClientBuilder: () -> PDCClient = { PoWebClient.initLocal(Relaynet.POWEB_PORT) },
    private val coroutineContext: CoroutineContext = Dispatchers.IO
) {

    @Throws(SendMessageException::class)
    suspend fun send(message: OutgoingMessage) {
        withContext(coroutineContext) {
            val senderPrivateKey = message.senderEndpoint.keyPair.private

            return@withContext try {
                pdcClientBuilder().use {
                    it.deliverParcel(
                        message.parcel.serialize(senderPrivateKey),
                        Signer(
                            message.parcel.senderCertificate,
                            senderPrivateKey
                        )
                    )
                }
            } catch (e: ServerException) {
                throw SendMessageException("Server error", e)
            } catch (e: ClientBindingException) {
                throw SendMessageException("Client error", e)
            } catch (e: RejectedParcelException) {
                throw SendMessageException("Parcel rejected by server", e)
            }
        }
    }
}

public class SendMessageException(message: String, cause: Throwable? = null)
    : GatewayRelaynetException(message, cause)
