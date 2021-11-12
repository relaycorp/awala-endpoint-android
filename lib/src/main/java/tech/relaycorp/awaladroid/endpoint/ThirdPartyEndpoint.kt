package tech.relaycorp.awaladroid.endpoint

import java.security.PublicKey
import tech.relaycorp.awaladroid.Awala
import tech.relaycorp.awaladroid.AwaladroidException
import tech.relaycorp.awaladroid.SetupPendingException
import tech.relaycorp.awaladroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.InvalidNodeConnectionParams
import tech.relaycorp.relaynet.PublicNodeConnectionParams
import tech.relaycorp.relaynet.SessionKey
import tech.relaycorp.relaynet.keystores.MissingKeyException
import tech.relaycorp.relaynet.wrappers.KeyException
import tech.relaycorp.relaynet.wrappers.deserializeRSAPublicKey
import tech.relaycorp.relaynet.wrappers.privateAddress
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import tech.relaycorp.relaynet.wrappers.x509.CertificateException

/**
 * An endpoint owned by a different instance of this app, or a different app in the same service.
 */
public sealed class ThirdPartyEndpoint(
    internal val identityKey: PublicKey
) : Endpoint(identityKey.privateAddress) {

    /**
     * Delete the endpoint.
     */
    @Throws(PersistenceException::class)
    public open suspend fun delete() {
        val context = Awala.getContextOrThrow()
        context.privateKeyStore.deleteSessionKeysForPeer(privateAddress)
        context.sessionPublicKeyStore.delete(privateAddress)
    }

    internal companion object {
        @Throws(PersistenceException::class)
        internal suspend fun load(
            firstPartyAddress: String,
            thirdPartyPrivateAddress: String
        ): ThirdPartyEndpoint? =
            PublicThirdPartyEndpoint.load(thirdPartyPrivateAddress)
                ?: PrivateThirdPartyEndpoint.load(thirdPartyPrivateAddress, firstPartyAddress)
    }
}

/**
 * A private third-party endpoint (i.e., one behind a different private gateway).
 *
 * @property firstPartyEndpointAddress The private address of the first-party endpoint linked to
 * this endpoint.
 */
public class PrivateThirdPartyEndpoint internal constructor(
    public val firstPartyEndpointAddress: String,
    identityKey: PublicKey,
    internal val pda: Certificate,
    internal val pdaChain: List<Certificate>
) : ThirdPartyEndpoint(identityKey) {

    override val address: String get() = privateAddress
    private val storageKey = "${firstPartyEndpointAddress}_$privateAddress"

    @Throws(PersistenceException::class, SetupPendingException::class)
    override suspend fun delete() {
        val context = Awala.getContextOrThrow()
        context.storage.privateThirdParty.delete(storageKey)
        super.delete()
    }

    public companion object {
        /**
         * Load an endpoint.
         */
        @Throws(PersistenceException::class, SetupPendingException::class)
        public suspend fun load(
            thirdPartyAddress: String,
            firstPartyAddress: String
        ): PrivateThirdPartyEndpoint? {
            val key = "${firstPartyAddress}_$thirdPartyAddress"
            val storage = Awala.getContextOrThrow().storage
            return storage.privateThirdParty.get(key)?.let { data ->
                PrivateThirdPartyEndpoint(
                    firstPartyAddress,
                    data.identityKey,
                    Certificate.deserialize(data.authBundle.pdaSerialized),
                    data.authBundle.pdaChainSerialized.map { Certificate.deserialize(it) }
                )
            }
        }

        /**
         * Create third-party endpoint by importing its PDA and chain.
         */
        @Throws(
            PersistenceException::class,
            UnknownFirstPartyEndpointException::class,
            InvalidAuthorizationException::class,
            InvalidThirdPartyEndpoint::class,
            SetupPendingException::class,
        )
        public suspend fun import(
            identityKeySerialized: ByteArray,
            authBundle: AuthorizationBundle,
            sessionKey: SessionKey,
        ): PrivateThirdPartyEndpoint {
            val context = Awala.getContextOrThrow()

            val identityKey = try {
                identityKeySerialized.deserializeRSAPublicKey()
            } catch (exp: KeyException) {
                throw InvalidThirdPartyEndpoint(
                    "Identity key is not a well-formed RSA public key",
                    exp,
                )
            }

            val pda = Certificate.deserialize(authBundle.pdaSerialized)
            val pdaChain = authBundle.pdaChainSerialized.map { Certificate.deserialize(it) }

            val firstPartyAddress = pda.subjectPrivateAddress
            try {
                context.privateKeyStore.retrieveIdentityKey(firstPartyAddress)
            } catch (exc: MissingKeyException) {
                throw UnknownFirstPartyEndpointException(
                    "First-party endpoint $firstPartyAddress is not registered"
                )
            }

            try {
                pda.validate()
            } catch (exc: CertificateException) {
                throw InvalidAuthorizationException("PDA is invalid", exc)
            }
            try {
                pda.getCertificationPath(emptyList(), pdaChain)
            } catch (e: CertificateException) {
                throw InvalidAuthorizationException("PDA was not issued by third-party endpoint", e)
            }

            val endpoint = PrivateThirdPartyEndpoint(
                firstPartyAddress,
                identityKey,
                pda,
                pdaChain
            )

            context.storage.privateThirdParty.set(
                endpoint.storageKey,
                PrivateThirdPartyEndpointData(identityKey, authBundle)
            )

            context.sessionPublicKeyStore.save(sessionKey, endpoint.privateAddress)

            return endpoint
        }
    }
}

/**
 * A public third-party endpoint (i.e., an Internet host in a centralized service).
 *
 * @property publicAddress The public address of the endpoint (e.g., "ping.awala.services").
 */
public class PublicThirdPartyEndpoint internal constructor(
    public val publicAddress: String,
    identityKey: PublicKey
) : ThirdPartyEndpoint(identityKey) {

    override val address: String get() = "https://$publicAddress"

    @Throws(PersistenceException::class, SetupPendingException::class)
    override suspend fun delete() {
        val context = Awala.getContextOrThrow()
        context.storage.publicThirdParty.delete(privateAddress)
        super.delete()
    }

    public companion object {
        /**
         * Load an endpoint by its [privateAddress].
         */
        @Throws(PersistenceException::class, SetupPendingException::class)
        public suspend fun load(privateAddress: String): PublicThirdPartyEndpoint? {
            val storage = Awala.getContextOrThrow().storage
            return storage.publicThirdParty.get(privateAddress)?.let {
                PublicThirdPartyEndpoint(it.publicAddress, it.identityKey)
            }
        }

        /**
         * Import the public endpoint using the specified [connectionParamsSerialized].
         *
         * @param connectionParamsSerialized The DER serialization of the connection parameters.
         */
        @Throws(
            PersistenceException::class,
            InvalidThirdPartyEndpoint::class,
            SetupPendingException::class,
        )
        public suspend fun import(
            connectionParamsSerialized: ByteArray
        ): PublicThirdPartyEndpoint {
            val context = Awala.getContextOrThrow()
            val connectionParams = try {
                PublicNodeConnectionParams.deserialize(connectionParamsSerialized)
            } catch (exc: InvalidNodeConnectionParams) {
                throw InvalidThirdPartyEndpoint(
                    "Connection params serialization is malformed",
                    exc,
                )
            }
            val peerPrivateAddress = connectionParams.identityKey.privateAddress
            context.storage.publicThirdParty.set(
                peerPrivateAddress,
                PublicThirdPartyEndpointData(
                    connectionParams.publicAddress,
                    connectionParams.identityKey
                )
            )
            context.sessionPublicKeyStore.save(
                connectionParams.sessionKey,
                peerPrivateAddress,
            )
            return PublicThirdPartyEndpoint(
                connectionParams.publicAddress,
                connectionParams.identityKey,
            )
        }
    }
}

public class UnknownThirdPartyEndpointException(message: String) : AwaladroidException(message)
public class UnknownFirstPartyEndpointException(message: String) : AwaladroidException(message)
public class InvalidThirdPartyEndpoint(message: String, cause: Throwable? = null) :
    AwaladroidException(message, cause)

public class InvalidAuthorizationException(message: String, cause: Throwable) :
    AwaladroidException(message, cause)
