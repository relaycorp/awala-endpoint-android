package tech.relaycorp.relaydroid.messaging

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.relaycorp.poweb.PoWebClient
import tech.relaycorp.relaydroid.Relaynet
import tech.relaycorp.relaydroid.RelaynetException
import tech.relaycorp.relaydroid.common.Logging.logger
import tech.relaycorp.relaynet.bindings.pdc.PDCClient
import tech.relaycorp.relaynet.bindings.pdc.Signer
import java.util.logging.Level
import kotlin.coroutines.CoroutineContext

internal class SendMessage(
    private val pdcClientBuilder: () -> PDCClient = { PoWebClient.initLocal(Relaynet.POWEB_PORT) },
    private val coroutineContext: CoroutineContext = Dispatchers.IO
) {

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
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Error sending message", e)
                throw SendMessageException("Could not deliver message to gateway", e)
            }
        }
    }
}

public class SendMessageException(message: String, cause: Throwable?)
    : RelaynetException(message, cause)
