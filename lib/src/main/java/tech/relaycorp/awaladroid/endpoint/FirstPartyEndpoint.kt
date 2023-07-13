package tech.relaycorp.awaladroid.endpoint

import java.security.PrivateKey
import java.security.PublicKey
import java.time.ZonedDateTime
import java.util.logging.Level
import tech.relaycorp.awaladroid.Awala
import tech.relaycorp.awaladroid.AwaladroidException
import tech.relaycorp.awaladroid.GatewayProtocolException
import tech.relaycorp.awaladroid.RegistrationFailedException
import tech.relaycorp.awaladroid.SetupPendingException
import tech.relaycorp.awaladroid.common.Logging.logger
import tech.relaycorp.awaladroid.common.toKeyPair
import tech.relaycorp.awaladroid.messaging.OutgoingMessage
import tech.relaycorp.awaladroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.PrivateEndpointConnParams
import tech.relaycorp.relaynet.issueDeliveryAuthorization
import tech.relaycorp.relaynet.keystores.KeyStoreBackendException
import tech.relaycorp.relaynet.keystores.MissingKeyException
import tech.relaycorp.relaynet.pki.CertificationPath
import tech.relaycorp.relaynet.wrappers.KeyException
import tech.relaycorp.relaynet.wrappers.deserializeRSAPublicKey
import tech.relaycorp.relaynet.wrappers.generateRSAKeyPair
import tech.relaycorp.relaynet.wrappers.nodeId
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import tech.relaycorp.relaynet.wrappers.x509.CertificateException

/**
 * An endpoint owned by the current instance of the app.
 */
public class FirstPartyEndpoint
internal constructor(
    internal val identityPrivateKey: PrivateKey,
    internal val identityCertificate: Certificate,
    internal val identityCertificateChain: List<Certificate>,
    public val internetAddress: String,
) : Endpoint(identityPrivateKey.nodeId) {

    /**
     * The RSA public key of the endpoint.
     */
    public val publicKey: PublicKey get() = identityCertificate.subjectPublicKey

    internal val pdaChain: List<Certificate>
        get() =
            listOf(identityCertificate) + identityCertificateChain

    /**
     * Issue a PDA for a third-party endpoint.
     */
    @Throws(CertificateException::class)
    public suspend fun issueAuthorization(
        thirdPartyEndpoint: ThirdPartyEndpoint,
        expiryDate: ZonedDateTime
    ): ByteArray =
        issueAuthorization(
            thirdPartyEndpoint.identityKey,
            expiryDate
        )

    /**
     * Issue a PDA for a third-party endpoint using its public key.
     */
    @Throws(CertificateException::class)
    public suspend fun issueAuthorization(
        thirdPartyEndpointPublicKeySerialized: ByteArray,
        expiryDate: ZonedDateTime
    ): ByteArray {
        val thirdPartyEndpointPublicKey =
            deserializePDAGranteePublicKey(thirdPartyEndpointPublicKeySerialized)
        return issueAuthorization(thirdPartyEndpointPublicKey, expiryDate)
    }

    @Throws(CertificateException::class)
    private suspend fun issueAuthorization(
        thirdPartyEndpointPublicKey: PublicKey,
        expiryDate: ZonedDateTime
    ): ByteArray {
        val pda = issueDeliveryAuthorization(
            subjectPublicKey = thirdPartyEndpointPublicKey,
            issuerPrivateKey = identityPrivateKey,
            validityEndDate = expiryDate,
            issuerCertificate = identityCertificate
        )
        val deliveryAuth = CertificationPath(pda, pdaChain)

        val context = Awala.getContextOrThrow()
        val sessionKeyPair = context.endpointManager.generateSessionKeyPair(nodeId, thirdPartyEndpointPublicKey.nodeId)

        val connParams = PrivateEndpointConnParams(
            this.publicKey,
            this.internetAddress,
            deliveryAuth,
            sessionKeyPair.sessionKey,
        )
        return connParams.serialize()
    }

    /**
     * Issue a PDA for a third-party endpoint and renew it indefinitely.
     */
    @Throws(CertificateException::class)
    public suspend fun authorizeIndefinitely(
        thirdPartyEndpoint: ThirdPartyEndpoint
    ): ByteArray =
        authorizeIndefinitely(thirdPartyEndpoint.identityKey)

    /**
     * Issue a PDA for a third-party endpoint (using its public key) and renew it indefinitely.
     */
    @Throws(CertificateException::class)
    public suspend fun authorizeIndefinitely(
        thirdPartyEndpointPublicKeySerialized: ByteArray
    ): ByteArray {
        val thirdPartyEndpointPublicKey =
            deserializePDAGranteePublicKey(thirdPartyEndpointPublicKeySerialized)
        return authorizeIndefinitely(thirdPartyEndpointPublicKey)
    }

    @Throws(CertificateException::class)
    private suspend fun authorizeIndefinitely(
        thirdPartyEndpointPublicKey: PublicKey,
    ): ByteArray {
        val authorization =
            issueAuthorization(thirdPartyEndpointPublicKey, identityCertificate.expiryDate)

        val context = Awala.getContextOrThrow()
        context.channelManager.create(this, thirdPartyEndpointPublicKey)

        return authorization
    }

    private fun deserializePDAGranteePublicKey(
        thirdPartyEndpointPublicKeySerialized: ByteArray
    ): PublicKey {
        val thirdPartyEndpointPublicKey = try {
            thirdPartyEndpointPublicKeySerialized.deserializeRSAPublicKey()
        } catch (exc: KeyException) {
            throw AuthorizationIssuanceException(
                "PDA grantee public key is not a valid RSA public key",
                exc
            )
        }
        return thirdPartyEndpointPublicKey
    }

    /**
     * Re-register endpoints after gateway certificate change
     */
    @Throws(
        RegistrationFailedException::class,
        GatewayProtocolException::class,
        PersistenceException::class,
        SetupPendingException::class,
    )
    internal suspend fun reRegister(): FirstPartyEndpoint {
        val context = Awala.getContextOrThrow()

        val registration = context.gatewayClient.registerEndpoint(identityPrivateKey.toKeyPair())
        val newEndpoint = FirstPartyEndpoint(
            identityPrivateKey,
            registration.privateNodeCertificate,
            listOf(registration.gatewayCertificate),
            registration.gatewayInternetAddress
        )

        val gatewayId = registration.gatewayCertificate.subjectId
        try {
            context.certificateStore.save(
                CertificationPath(
                    registration.privateNodeCertificate,
                    listOf(registration.gatewayCertificate),
                ),
                gatewayId,
            )
        } catch (exc: KeyStoreBackendException) {
            throw PersistenceException("Failed to save certificate", exc)
        }

        return newEndpoint
    }

    internal suspend fun reissuePDAs() {
        val context = Awala.getContextOrThrow()
        val thirdPartyEndpointAddresses = context.channelManager.getLinkedEndpointAddresses(this)
        for (thirdPartyEndpointAddress in thirdPartyEndpointAddresses) {
            val thirdPartyEndpoint = ThirdPartyEndpoint.load(
                this@FirstPartyEndpoint.nodeId,
                thirdPartyEndpointAddress
            )
            if (thirdPartyEndpoint == null) {
                logger.log(
                    Level.INFO,
                    "Ignoring missing third-party endpoint $thirdPartyEndpointAddress"
                )
                break
            }

            val message = OutgoingMessage.build(
                "application/vnd+relaycorp.awala.pda-path",
                issueAuthorization(thirdPartyEndpoint, identityCertificate.expiryDate),
                this,
                thirdPartyEndpoint,
                identityCertificate.expiryDate,
            )
            context.gatewayClient.sendMessage(message)
        }
    }

    /**
     * Delete the endpoint.
     */
    @Throws(PersistenceException::class, SetupPendingException::class)
    public suspend fun delete() {
        val context = Awala.getContextOrThrow()
        context.privateKeyStore.deleteKeys(nodeId)
        context.certificateStore.delete(nodeId, identityCertificate.issuerCommonName)
        context.channelManager.delete(this)
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
                listOf(registration.gatewayCertificate),
                registration.gatewayInternetAddress
            )

            try {
                context.privateKeyStore.saveIdentityKey(
                    keyPair.private,
                )
            } catch (exc: KeyStoreBackendException) {
                throw PersistenceException("Failed to save identity key", exc)
            }

            val gatewayId = registration.gatewayCertificate.subjectId
            try {
                context.certificateStore.save(
                    CertificationPath(
                        registration.privateNodeCertificate,
                        listOf(registration.gatewayCertificate),
                    ),
                    gatewayId
                )
            } catch (exc: KeyStoreBackendException) {
                throw PersistenceException("Failed to save certificate", exc)
            }

            context.storage.gatewayId.set(
                endpoint.nodeId,
                gatewayId,
            )

            context.storage.internetAddress.set(
                registration.gatewayInternetAddress
            )

            return endpoint
        }

        /**
         * Load an endpoint by its address.
         */
        @Throws(PersistenceException::class, SetupPendingException::class)
        public suspend fun load(nodeId: String): FirstPartyEndpoint? {
            val context = Awala.getContextOrThrow()
            val identityPrivateKey = try {
                context.privateKeyStore.retrieveIdentityKey(nodeId)
            } catch (exc: MissingKeyException) {
                return null
            } catch (exc: KeyStoreBackendException) {
                throw PersistenceException("Failed to load private key of endpoint", exc)
            }
            val gatewayNodeId = context.storage.gatewayId.get(nodeId)
                ?: throw PersistenceException("Failed to load gateway address for endpoint")
            val certificatePath = try {
                context.certificateStore.retrieveLatest(
                    nodeId, gatewayNodeId
                )
                    ?: return null
            } catch (exc: KeyStoreBackendException) {
                throw PersistenceException("Failed to load certificate for endpoint", exc)
            }

            val internetAddress: String = context.storage.internetAddress.get()
                ?: throw PersistenceException(
                    "Failed to load gateway internet address for endpoint"
                )

            return FirstPartyEndpoint(
                identityPrivateKey,
                certificatePath.leafCertificate,
                certificatePath.certificateAuthorities,
                internetAddress,
            )
        }
    }
}

/**
 * Failure to issue a PDA.
 */
public class AuthorizationIssuanceException(message: String, cause: Throwable) :
    AwaladroidException(message, cause)
