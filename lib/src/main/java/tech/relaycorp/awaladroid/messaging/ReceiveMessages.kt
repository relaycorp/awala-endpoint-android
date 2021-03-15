package tech.relaycorp.awaladroid.messaging

import java.util.logging.Level
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import tech.relaycorp.awaladroid.Awala
import tech.relaycorp.awaladroid.GatewayException
import tech.relaycorp.awaladroid.GatewayProtocolException
import tech.relaycorp.awaladroid.Storage
import tech.relaycorp.awaladroid.common.Logging.logger
import tech.relaycorp.awaladroid.endpoint.UnknownFirstPartyEndpointException
import tech.relaycorp.awaladroid.storage.persistence.PersistenceException
import tech.relaycorp.poweb.PoWebClient
import tech.relaycorp.relaynet.bindings.pdc.ClientBindingException
import tech.relaycorp.relaynet.bindings.pdc.NonceSignerException
import tech.relaycorp.relaynet.bindings.pdc.PDCClient
import tech.relaycorp.relaynet.bindings.pdc.ParcelCollection
import tech.relaycorp.relaynet.bindings.pdc.ServerException
import tech.relaycorp.relaynet.bindings.pdc.Signer
import tech.relaycorp.relaynet.bindings.pdc.StreamingMode
import tech.relaycorp.relaynet.messages.InvalidMessageException
import tech.relaycorp.relaynet.ramf.RAMFException
import tech.relaycorp.relaynet.wrappers.cms.EnvelopedDataException

internal class ReceiveMessages(
    private val pdcClientBuilder: () -> PDCClient = { PoWebClient.initLocal(Awala.POWEB_PORT) }
) {

    @Throws(
        ReceiveMessageException::class,
        GatewayProtocolException::class,
        PersistenceException::class
    )
    fun receive(): Flow<IncomingMessage> =
        getNonceSigners()
            .flatMapLatest { nonceSigners ->
                pdcClientBuilder().use {
                    try {
                        collectParcels(it, nonceSigners)
                    } catch (exp: ServerException) {
                        throw ReceiveMessageException("Server error", exp)
                    } catch (exp: ClientBindingException) {
                        throw GatewayProtocolException("Client error", exp)
                    } catch (exp: NonceSignerException) {
                        throw GatewayProtocolException("Client signing error", exp)
                    }
                }
            }

    @Throws(PersistenceException::class)
    private fun getNonceSigners() = suspend {
        Storage
            .identityCertificate
            .list()
            .map { endpoint ->
                Signer(
                    Storage.identityCertificate.get(endpoint)!!,
                    Storage.identityKeyPair.get(endpoint)!!.private
                )
            }
            .toTypedArray()
    }.asFlow()

    @Throws(PersistenceException::class)
    private suspend fun collectParcels(pdcClient: PDCClient, nonceSigners: Array<Signer>) =
        pdcClient
            .collectParcels(nonceSigners, StreamingMode.CloseUponCompletion)
            .mapNotNull { parcelCollection ->
                val parcel = try {
                    parcelCollection.deserializeAndValidateParcel()
                } catch (exp: RAMFException) {
                    parcelCollection.disregard("Malformed incoming parcel", exp)
                    return@mapNotNull null
                } catch (exp: InvalidMessageException) {
                    parcelCollection.disregard("Invalid incoming parcel", exp)
                    return@mapNotNull null
                }
                try {
                    IncomingMessage.build(parcel) { parcelCollection.ack() }
                } catch (exp: UnknownFirstPartyEndpointException) {
                    parcelCollection.disregard("Incoming parcel with invalid recipient", exp)
                    return@mapNotNull null
                } catch (exp: UnknownFirstPartyEndpointException) {
                    parcelCollection.disregard("Incoming parcel issues with invalid sender", exp)
                    return@mapNotNull null
                } catch (exp: EnvelopedDataException) {
                    parcelCollection.disregard(
                        "Failed to decrypt parcel; sender might have used wrong key",
                        exp
                    )
                    return@mapNotNull null
                } catch (exp: InvalidMessageException) {
                    parcelCollection.disregard(
                        "Incoming parcel did not encapsulate a valid service message",
                        exp
                    )
                    return@mapNotNull null
                }
            }
}

private suspend fun ParcelCollection.disregard(reason: String, exc: Throwable) {
    logger.log(Level.WARNING, reason, exc)
    ack()
}

/**
 * The private gateway failed to give us incoming messages.
 *
 * This is most likely to be a bug in the gateway and retrying later may work.
 */
public class ReceiveMessageException(message: String, throwable: Throwable? = null) :
    GatewayException(message, throwable)
