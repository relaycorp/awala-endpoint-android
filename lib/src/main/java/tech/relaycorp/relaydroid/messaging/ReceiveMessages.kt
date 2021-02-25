package tech.relaycorp.relaydroid.messaging

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import tech.relaycorp.poweb.PoWebClient
import tech.relaycorp.relaydroid.GatewayException
import tech.relaycorp.relaydroid.GatewayProtocolException
import tech.relaycorp.relaydroid.Relaynet
import tech.relaycorp.relaydroid.Storage
import tech.relaycorp.relaydroid.common.Logging.logger
import tech.relaycorp.relaynet.bindings.pdc.ClientBindingException
import tech.relaycorp.relaynet.bindings.pdc.NonceSignerException
import tech.relaycorp.relaynet.bindings.pdc.PDCClient
import tech.relaycorp.relaynet.bindings.pdc.ServerException
import tech.relaycorp.relaynet.bindings.pdc.Signer
import tech.relaycorp.relaynet.bindings.pdc.StreamingMode
import tech.relaycorp.relaynet.messages.InvalidMessageException
import tech.relaycorp.relaynet.ramf.RAMFException
import java.util.logging.Level
import kotlin.jvm.Throws

internal class ReceiveMessages(
    private val pdcClientBuilder: () -> PDCClient = { PoWebClient.initLocal(Relaynet.POWEB_PORT) }
) {

    @Throws(
        ReceiveMessagesException::class,
        GatewayProtocolException::class
    )
    fun receive(): Flow<IncomingMessage> =
        getNonceSigners()
            .flatMapLatest { nonceSigners ->
                pdcClientBuilder().use {
                    try {
                        collectParcels(it, nonceSigners)
                    } catch (exp: ServerException) {
                        throw ReceiveMessagesException("Server error", exp)
                    } catch (exp: ClientBindingException) {
                        throw GatewayProtocolException("Client error", exp)
                    } catch (exp: NonceSignerException) {
                        throw GatewayProtocolException("Client signing error", exp)
                    }
                }
            }

    private fun getNonceSigners() = suspend {
        Storage
            .listEndpoints()
            .map { endpoint ->
                Signer(
                    Storage.getIdentityCertificate(endpoint)!!,
                    Storage.getIdentityKeyPair(endpoint)!!.private
                )
            }
            .toTypedArray()
    }.asFlow()

    private suspend fun collectParcels(pdcClient: PDCClient, nonceSigners: Array<Signer>) =
        pdcClient
            .collectParcels(nonceSigners, StreamingMode.CloseUponCompletion)
            .mapNotNull { parcelCollection ->
                val parcel = try {
                    parcelCollection.deserializeAndValidateParcel()
                } catch (exp: RAMFException) {
                    logger.log(Level.WARNING, "Malformed incoming parcel", exp)
                    parcelCollection.ack()
                    return@mapNotNull null
                } catch (exp: InvalidMessageException) {
                    logger.log(Level.WARNING, "Invalid incoming parcel", exp)
                    parcelCollection.ack()
                    return@mapNotNull null
                }
                IncomingMessage.build(parcel) { parcelCollection.ack() }
            }
}

public class ReceiveMessagesException(message: String, throwable: Throwable? = null)
    : GatewayException(message, throwable)
