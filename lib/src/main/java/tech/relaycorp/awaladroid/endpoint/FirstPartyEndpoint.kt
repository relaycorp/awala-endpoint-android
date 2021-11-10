package tech.relaycorp.awaladroid.endpoint

import java.security.PrivateKey
import java.security.PublicKey
import java.time.ZonedDateTime
import tech.relaycorp.awaladroid.Awala
import tech.relaycorp.awaladroid.AwaladroidException
import tech.relaycorp.awaladroid.GatewayProtocolException
import tech.relaycorp.awaladroid.RegistrationFailedException
import tech.relaycorp.awaladroid.SetupPendingException
import tech.relaycorp.awaladroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.issueDeliveryAuthorization
import tech.relaycorp.relaynet.keystores.MissingKeyException
import tech.relaycorp.relaynet.wrappers.KeyException
import tech.relaycorp.relaynet.wrappers.deserializeRSAPublicKey
import tech.relaycorp.relaynet.wrappers.generateRSAKeyPair
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import tech.relaycorp.relaynet.wrappers.x509.CertificateException

/**
 * An endpoint owned by the current instance of the app.
 */
public class FirstPartyEndpoint
internal constructor(
    internal val identityPrivateKey: PrivateKey,
    internal val identityCertificate: Certificate,
    internal val gatewayCertificate: Certificate
) : Endpoint(identityCertificate.subjectPrivateAddress) {

    public override val address: String get() = privateAddress

    /**
     * The RSA public key of the endpoint.
     */
    public val publicKey: PublicKey get() = identityCertificate.subjectPublicKey

    internal val pdaChain: List<Certificate> get() = listOf(identityCertificate, gatewayCertificate)

    /**
     * Issue a PDA for a third-party endpoint.
     */
    @Throws(CertificateException::class)
    public fun issueAuthorization(
        thirdPartyEndpoint: ThirdPartyEndpoint,
        expiryDate: ZonedDateTime
    ): AuthorizationBundle =
        issueAuthorization(
            thirdPartyEndpoint.identityKey,
            expiryDate
        )

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
            issuerPrivateKey = identityPrivateKey,
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
    @Throws(PersistenceException::class, SetupPendingException::class)
    public suspend fun delete() {
        val storage = Awala.getContextOrThrow().storage
        storage.identityKeyPair.delete(address)
        storage.identityCertificate.delete(address)
    }

    public companion object {
        /**
         * Generate endpoint and register it with the private gateway.
         */
        @Throws(
            RegistrationFailedException::class,
            GatewayProtocolException::class,
            PersistenceException::class,
            SetupPendingException::class,
        )
        public suspend fun register(): FirstPartyEndpoint {
            val context = Awala.getContextOrThrow()
            val keyPair = generateRSAKeyPair()

            val registration = context.gatewayClient.registerEndpoint(keyPair)
            val endpoint = FirstPartyEndpoint(
                keyPair.private,
                registration.privateNodeCertificate,
                registration.gatewayCertificate
            )

            context.privateKeyStore.saveIdentityKey(
                keyPair.private,
                endpoint.identityCertificate,
            )

            context.storage.gatewayCertificate.set(endpoint.gatewayCertificate)

            return endpoint
        }

        /**
         * Load an endpoint by its address.
         */
        @Throws(PersistenceException::class, SetupPendingException::class)
        public suspend fun load(privateAddress: String): FirstPartyEndpoint? {
            val context = Awala.getContextOrThrow()
            val identityKeyPair = try {
                context.privateKeyStore.retrieveIdentityKey(privateAddress)
            } catch (exc: MissingKeyException) {
                return null
            }
            return context.storage.gatewayCertificate.get()?.let { gwCertificate ->
                FirstPartyEndpoint(
                    identityKeyPair.privateKey,
                    identityKeyPair.certificate,
                    gwCertificate
                )
            }
        }
    }
}

/**
 * Failure to issue a PDA.
 */
public class AuthorizationIssuanceException(message: String, cause: Throwable) :
    AwaladroidException(message, cause)
