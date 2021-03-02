package tech.relaycorp.relaydroid

import tech.relaycorp.relaydroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.wrappers.generateRSAKeyPair
import tech.relaycorp.relaynet.wrappers.privateAddress
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import java.security.KeyPair
import java.security.PublicKey

public class FirstPartyEndpoint
internal constructor(
    internal val keyPair: KeyPair,
    internal val certificate: Certificate,
    internal val gatewayCertificate: Certificate
) : Endpoint {

    public override val thirdPartyAddress: String get() = keyPair.public.privateAddress

    public val publicKey: PublicKey get() = keyPair.public

    @Throws(PersistenceException::class)
    public suspend fun remove() {
        Storage.identityKeyPair.delete(thirdPartyAddress)
        Storage.identityCertificate.delete(thirdPartyAddress)
    }

    @Throws(PersistenceException::class)
    private suspend fun store() {
        Storage.identityKeyPair.set(thirdPartyAddress, keyPair)
        Storage.identityCertificate.set(thirdPartyAddress, certificate)
        Storage.gatewayCertificate.set(gatewayCertificate)
    }

    public companion object {
        @Throws(
            RegistrationFailedException::class,
            GatewayProtocolException::class,
            PersistenceException::class
        )
        public suspend fun register(): FirstPartyEndpoint {
            val keyPair = generateRSAKeyPair()
            val registration = GatewayClient.registerEndpoint(keyPair)
            val endpoint = FirstPartyEndpoint(
                keyPair,
                registration.privateNodeCertificate,
                registration.gatewayCertificate
            )
            endpoint.store()
            return endpoint
        }

        @Throws(PersistenceException::class)
        public suspend fun load(address: String): FirstPartyEndpoint? {
            return Storage.identityKeyPair.get(address)?.let { keyPair ->
                Storage.identityCertificate.get(address)?.let { certificate ->
                    Storage.gatewayCertificate.get()?.let { gwCertificate ->
                        FirstPartyEndpoint(
                            keyPair,
                            certificate,
                            gwCertificate
                        )
                    }
                }
            }
        }
    }
}
