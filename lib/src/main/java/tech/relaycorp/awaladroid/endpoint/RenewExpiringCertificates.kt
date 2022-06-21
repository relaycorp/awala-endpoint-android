package tech.relaycorp.awaladroid.endpoint

import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.days
import tech.relaycorp.relaynet.keystores.PrivateKeyStore
import tech.relaycorp.relaynet.wrappers.privateAddress
import tech.relaycorp.relaynet.wrappers.x509.Certificate

internal class RenewExpiringCertificates(
    private val privateKeyStore: PrivateKeyStore,
    private val firstPartyEndpointLoader: suspend (String) -> FirstPartyEndpoint?
) {

    suspend operator fun invoke() {
        privateKeyStore.retrieveAllIdentityKeys()
            .mapNotNull { firstPartyEndpointLoader(it.privateAddress) }
            .forEach {
                if (it.identityCertificate.isExpiring) {
                    it.reRegister()
                }
            }
    }

    private val Certificate.isExpiring get() =
        expiryDate <= ZonedDateTime.now().plusSeconds(EXPIRATION_THRESHOLD.inWholeSeconds)

    companion object {
        internal val EXPIRATION_THRESHOLD = 60.days
    }
}
