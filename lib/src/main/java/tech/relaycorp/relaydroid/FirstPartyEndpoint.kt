package tech.relaycorp.relaydroid

import tech.relaycorp.relaynet.wrappers.generateRSAKeyPair
import tech.relaycorp.relaynet.wrappers.privateAddress
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import java.security.KeyPair

public class FirstPartyEndpoint
internal constructor(
    internal val keyPair: KeyPair,
    internal val certificate: Certificate,
    internal val gatewayCertificate: Certificate
) : Endpoint {

    public override val address: String get() = keyPair.public.privateAddress

    public suspend fun remove() {
        Storage.deleteIdentityKeyPair(address)
        Storage.deleteIdentityCertificate(address)
    }

    private suspend fun store() {
        Storage.setIdentityKeyPair(address, keyPair)
        Storage.setIdentityCertificate(address, certificate)
        Storage.setGatewayCertificate(gatewayCertificate)
    }

    public companion object {
        @Throws(
            RegistrationFailedException::class,
            GatewayProtocolException::class
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

        public suspend fun load(address: String): FirstPartyEndpoint? {
            return Storage.getIdentityKeyPair(address)?.let { keyPair ->
                Storage.getIdentityCertificate(address)?.let { certificate ->
                    Storage.getGatewayCertificate()?.let { gwCertificate ->
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
