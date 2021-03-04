package tech.relaycorp.relaydroid.endpoint

import tech.relaycorp.relaydroid.GatewayClient
import tech.relaycorp.relaydroid.GatewayProtocolException
import tech.relaycorp.relaydroid.RegistrationFailedException
import tech.relaycorp.relaydroid.Storage
import tech.relaycorp.relaydroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.issueDeliveryAuthorization
import tech.relaycorp.relaynet.wrappers.generateRSAKeyPair
import tech.relaycorp.relaynet.wrappers.privateAddress
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import tech.relaycorp.relaynet.wrappers.x509.CertificateException
import java.security.KeyPair
import java.security.PublicKey
import java.time.ZonedDateTime

public class FirstPartyEndpoint
internal constructor(
    internal val keyPair: KeyPair,
    internal val certificate: Certificate,
    internal val gatewayCertificate: Certificate
) : Endpoint {

    public override val address: String get() = keyPair.public.privateAddress

    public val publicKey: PublicKey get() = keyPair.public

    @Throws(CertificateException::class)
    public fun issueAuthorization(
        privateThirdPartyPublicKey: PublicKey,
        expiryDate: ZonedDateTime
    ): Certificate =
        issueDeliveryAuthorization(
            subjectPublicKey = privateThirdPartyPublicKey,
            issuerPrivateKey = keyPair.private,
            validityEndDate = expiryDate,
            issuerCertificate = certificate
        )

    @Throws(PersistenceException::class)
    public suspend fun delete() {
        Storage.identityKeyPair.delete(address)
        Storage.identityCertificate.delete(address)
    }

    @Throws(PersistenceException::class)
    private suspend fun store() {
        Storage.identityKeyPair.set(address, keyPair)
        Storage.identityCertificate.set(address, certificate)
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
