package tech.relaycorp.relaydroid.messaging

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import tech.relaycorp.poweb.PoWebClient
import tech.relaycorp.relaydroid.Relaynet
import tech.relaycorp.relaydroid.Storage
import tech.relaycorp.relaynet.bindings.pdc.PDCClient
import tech.relaycorp.relaynet.bindings.pdc.Signer
import tech.relaycorp.relaynet.bindings.pdc.StreamingMode
import tech.relaycorp.relaynet.messages.Parcel

internal class ReceiveMessages(
    private val pdcClientBuilder: () -> PDCClient = { PoWebClient.initLocal(Relaynet.POWEB_PORT) }
) {

    fun receive() =
        getNonceSigners()
            .flatMapLatest { nonceSigners ->
                pdcClientBuilder().use {
                    it.collectParcels(nonceSigners, StreamingMode.CloseUponCompletion)
                        .map { parcelCollection ->
                            val parcel = Parcel.deserialize(parcelCollection.parcelSerialized)
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
