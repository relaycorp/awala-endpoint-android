package tech.relaycorp.awaladroid.endpoint

import tech.relaycorp.relaynet.keystores.PrivateKeyStore
import tech.relaycorp.relaynet.wrappers.nodeId

internal class HandleGatewayCertificateChange(
    private val privateKeyStore: PrivateKeyStore
) {

    suspend operator fun invoke() {
        privateKeyStore.retrieveAllIdentityKeys()
            .mapNotNull { FirstPartyEndpoint.load(it.nodeId) }
            .forEach {
                it.reRegister()
                it.reissuePDAs()
            }
    }
}
