package tech.relaycorp.relaydroid.messaging

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import tech.relaycorp.poweb.PoWebClient
import tech.relaycorp.relaydroid.Relaynet
import tech.relaycorp.relaydroid.Storage
import tech.relaycorp.relaydroid.common.Logging.logger
import tech.relaycorp.relaynet.bindings.pdc.PDCClient
import tech.relaycorp.relaynet.bindings.pdc.Signer
import tech.relaycorp.relaynet.bindings.pdc.StreamingMode
import tech.relaycorp.relaynet.ramf.RAMFException
import java.util.logging.Level

internal class ReceiveMessages(
    private val pdcClientBuilder: () -> PDCClient = { PoWebClient.initLocal(Relaynet.POWEB_PORT) }
) {

    fun receive(): Flow<IncomingMessage> =
        getNonceSigners()
            .flatMapLatest { nonceSigners ->
                pdcClientBuilder().use {
                    it.collectParcels(nonceSigners, StreamingMode.CloseUponCompletion)
                        .mapNotNull { parcelCollection ->
                            val parcel = try {
                                parcelCollection.deserializeAndValidateParcel()
                            } catch (exp: RAMFException) {
                                logger.log(Level.WARNING, "Invalid incoming parcel", exp)
                                parcelCollection.ack()
                                return@mapNotNull null
                            }
                            IncomingMessage.build(parcel) { parcelCollection.ack() }
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
}
