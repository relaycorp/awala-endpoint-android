package tech.relaycorp.awaladroid.messaging

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import tech.relaycorp.awaladroid.Awala
import tech.relaycorp.awaladroid.GatewayException
import tech.relaycorp.awaladroid.GatewayProtocolException
import tech.relaycorp.awaladroid.common.Logging.logger
import tech.relaycorp.awaladroid.endpoint.UnknownFirstPartyEndpointException
import tech.relaycorp.awaladroid.endpoint.UnknownThirdPartyEndpointException
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
import tech.relaycorp.relaynet.ramf.InvalidPayloadException
import tech.relaycorp.relaynet.ramf.RAMFException
import tech.relaycorp.relaynet.wrappers.cms.EnvelopedDataException
import tech.relaycorp.relaynet.wrappers.nodeId
import java.util.logging.Level

internal class ReceiveMessages(
    private val pdcClientBuilder: () -> PDCClient = { PoWebClient.initLocal(Awala.POWEB_PORT) },
) {
    /**
     * Flow may throw:
     * - ReceiveMessageException
     * - GatewayProtocolException
     */
    @Throws(PersistenceException::class)
    fun receive(): Flow<IncomingMessage> =
        getNonceSigners()
            .flatMapLatest { nonceSigners ->
                if (nonceSigners.isEmpty()) {
                    logger.log(
                        Level.WARNING,
                        "Skipping parcel collection because there are no first-party endpoints",
                    )
                    return@flatMapLatest emptyFlow()
                }

                val pdcClient = pdcClientBuilder()
                collectParcels(pdcClient, nonceSigners)
                    .catch {
                        throw when (it) {
                            is ServerException ->
                                ReceiveMessageException("Server error", it)

                            is ClientBindingException ->
                                GatewayProtocolException("Client error", it)

                            is NonceSignerException ->
                                GatewayProtocolException("Client signing error", it)

                            else -> it
                        }
                    }
                    .onCompletion {
                        @Suppress("BlockingMethodInNonBlockingContext")
                        pdcClient.close()
                    }
            }

    @Throws(PersistenceException::class)
    private fun getNonceSigners() =
        suspend {
            val context = Awala.getContextOrThrow()
            context.privateKeyStore.retrieveAllIdentityKeys()
                .flatMap { identityPrivateKey ->
                    val nodeId = identityPrivateKey.nodeId
                    val privateGatewayId =
                        context.storage.gatewayId.get(nodeId)
                            ?: return@flatMap emptyList()
                    context.certificateStore.retrieveAll(
                        nodeId,
                        privateGatewayId,
                    ).map {
                        Signer(
                            it.leafCertificate,
                            identityPrivateKey,
                        )
                    }
                }
                .toTypedArray()
        }.asFlow()

    /**
     * Flow may throw:
     * - ReceiveMessageException
     * - GatewayProtocolException
     */
    @Throws(PersistenceException::class)
    private suspend fun collectParcels(
        pdcClient: PDCClient,
        nonceSigners: Array<Signer>,
    ) = pdcClient
        .collectParcels(nonceSigners, StreamingMode.CloseUponCompletion)
        .mapNotNull { parcelCollection ->
            val parcel =
                try {
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
            } catch (exp: UnknownThirdPartyEndpointException) {
                parcelCollection.disregard("Incoming parcel issues with invalid sender", exp)
                return@mapNotNull null
            } catch (exp: EnvelopedDataException) {
                parcelCollection.disregard(
                    "Failed to decrypt parcel; sender might have used wrong key",
                    exp,
                )
                return@mapNotNull null
            } catch (exp: InvalidPayloadException) {
                parcelCollection.disregard(
                    "Incoming parcel did not encapsulate a valid service message",
                    exp,
                )
                return@mapNotNull null
            }
        }
}

private suspend fun ParcelCollection.disregard(
    reason: String,
    exc: Throwable,
) {
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
