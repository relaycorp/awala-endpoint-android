package tech.relaycorp.awaladroid.endpoint

import tech.relaycorp.relaynet.keystores.PrivateKeyStore
import tech.relaycorp.relaynet.wrappers.privateAddress

internal class HandleGatewayCertificateChange(
    private val privateKeyStore: PrivateKeyStore
) {

    suspend operator fun invoke() {
        privateKeyStore.retrieveAllIdentityKeys()
            .mapNotNull { FirstPartyEndpoint.load(it.privateAddress) }
            .forEach {
                it.reRegister()
                it.reissuePDAs()
            }
    }
}
