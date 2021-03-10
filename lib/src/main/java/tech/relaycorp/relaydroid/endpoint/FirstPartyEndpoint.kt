package tech.relaycorp.relaydroid.endpoint

import java.security.KeyPair
import java.security.PublicKey
import java.time.ZonedDateTime
import tech.relaycorp.relaydroid.GatewayClient
import tech.relaycorp.relaydroid.GatewayProtocolException
import tech.relaycorp.relaydroid.RegistrationFailedException
import tech.relaycorp.relaydroid.RelaydroidException
import tech.relaycorp.relaydroid.Storage
import tech.relaycorp.relaydroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.issueDeliveryAuthorization
import tech.relaycorp.relaynet.wrappers.KeyException
import tech.relaycorp.relaynet.wrappers.deserializeRSAPublicKey
import tech.relaycorp.relaynet.wrappers.generateRSAKeyPair
import tech.relaycorp.relaynet.wrappers.privateAddress
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import tech.relaycorp.relaynet.wrappers.x509.CertificateException

/**
 * An endpoint owned by the current instance of the app.
 */
public class FirstPartyEndpoint
internal constructor(
    internal val keyPair: KeyPair,
    internal val identityCertificate: Certificate,
    internal val gatewayCertificate: Certificate
) : Endpoint {

    public override val address: String get() = keyPair.public.privateAddress

    /**
     * The RSA public key of the endpoint.
     */
    public val publicKey: PublicKey get() = keyPair.public

    internal val pdaChain: List<Certificate> get() = listOf(identityCertificate, gatewayCertificate)

    /**
     * Issue a PDA for a third-party endpoint.
     */
    @Throws(CertificateException::class)
    public fun issueAuthorization(
        thirdPartyEndpoint: ThirdPartyEndpoint,
        expiryDate: ZonedDateTime
    ): AuthorizationBundle {
        return issueAuthorization(
            thirdPartyEndpoint.identityCertificate.subjectPublicKey,
            expiryDate
        )
    }

    /**
     * Issue a PDA for a third-party endpoint using its public key.
     */
    @Throws(CertificateException::class)
    public fun issueAuthorization(
        thirdPartyEndpointPublicKeySerialized: ByteArray,
        expiryDate: ZonedDateTime
    ): AuthorizationBundle {
        val thirdPartyEndpointPublicKey = try {
            thirdPartyEndpointPublicKeySerialized.deserializeRSAPublicKey()
        } catch (exc: KeyException) {
            throw AuthorizationIssuanceException(
                "PDA grantee public key is not a valid RSA public key",
                exc
            )
        }
        return issueAuthorization(thirdPartyEndpointPublicKey, expiryDate)
    }

    @Throws(CertificateException::class)
    private fun issueAuthorization(
        thirdPartyEndpointPublicKey: PublicKey,
        expiryDate: ZonedDateTime
    ): AuthorizationBundle {
        val pda = issueDeliveryAuthorization(
            subjectPublicKey = thirdPartyEndpointPublicKey,
            issuerPrivateKey = keyPair.private,
            validityEndDate = expiryDate,
            issuerCertificate = identityCertificate
        )
        return AuthorizationBundle(
            pda.serialize(),
            pdaChain.map { it.serialize() }
        )
    }

    /**
     * Delete the endpoint.
     */
    @Throws(PersistenceException::class)
    public suspend fun delete() {
        Storage.identityKeyPair.delete(address)
        Storage.identityCertificate.delete(address)
    }

    @Throws(PersistenceException::class)
    private suspend fun store() {
        Storage.identityKeyPair.set(address, keyPair)
        Storage.identityCertificate.set(address, identityCertificate)
        Storage.gatewayCertificate.set(gatewayCertificate)
    }

    public companion object {
        /**
         * Generate endpoint and register it with the private gateway.
         */
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

        /**
         * Load an endpoint by its address.
         */
        @Throws(PersistenceException::class)
        public suspend fun load(privateAddress: String): FirstPartyEndpoint? {
            return Storage.identityKeyPair.get(privateAddress)?.let { keyPair ->
                Storage.identityCertificate.get(privateAddress)?.let { certificate ->
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

/**
 * Failure to issue a PDA.
 */
public class AuthorizationIssuanceException(message: String, cause: Throwable) :
    RelaydroidException(message, cause)
