package tech.relaycorp.awaladroid.endpoint

import tech.relaycorp.awaladroid.AwaladroidException
import tech.relaycorp.awaladroid.Storage
import tech.relaycorp.awaladroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import tech.relaycorp.relaynet.wrappers.x509.CertificateException

/**
 * An endpoint owned by a different instance of this app, or a different app in the same service.
 */
public sealed class ThirdPartyEndpoint(
    identityCertificate: Certificate
) : Endpoint(identityCertificate) {

    /**
     * Delete the endpoint.
     */
    @Throws(PersistenceException::class)
    public abstract suspend fun delete()

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
    identityCertificate: Certificate,
    internal val pda: Certificate,
    internal val pdaChain: List<Certificate>
) : ThirdPartyEndpoint(identityCertificate) {

    override val address: String get() = privateAddress
    private val storageKey = "${firstPartyEndpointAddress}_$privateAddress"

    @Throws(PersistenceException::class)
    override suspend fun delete() {
        Storage.privateThirdParty.delete(storageKey)
    }

    public companion object {
        /**
         * Load an endpoint.
         */
        @Throws(PersistenceException::class)
        public suspend fun load(
            thirdPartyAddress: String,
            firstPartyAddress: String
        ): PrivateThirdPartyEndpoint? {
            val key = "${firstPartyAddress}_$thirdPartyAddress"
            return Storage.privateThirdParty.get(key)?.let { data ->
                PrivateThirdPartyEndpoint(
                    firstPartyAddress,
                    data.identityCertificate,
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
            InvalidAuthorizationException::class
        )
        public suspend fun import(
            identityCertificate: ByteArray,
            authBundle: AuthorizationBundle
        ): PrivateThirdPartyEndpoint {

            val identityCertificateDeserialized = try {
                Certificate.deserialize(identityCertificate)
            } catch (exp: CertificateException) {
                throw InvalidAuthorizationException("Invalid identity certificate", exp)
            }

            val pda = Certificate.deserialize(authBundle.pdaSerialized)
            val pdaChain = authBundle.pdaChainSerialized.map { Certificate.deserialize(it) }

            val firstPartyAddress = pda.subjectPrivateAddress

            Storage.identityCertificate.get(firstPartyAddress)
                ?: throw UnknownFirstPartyEndpointException(
                    "First party endpoint $firstPartyAddress not registered"
                )

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
                identityCertificateDeserialized,
                pda,
                pdaChain
            )

            Storage.privateThirdParty.set(
                endpoint.storageKey,
                PrivateThirdPartyEndpointData(identityCertificateDeserialized, authBundle)
            )

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
    identityCertificate: Certificate
) : ThirdPartyEndpoint(identityCertificate) {

    override val address: String get() = "https://$publicAddress"

    @Throws(PersistenceException::class)
    override suspend fun delete() {
        Storage.publicThirdParty.delete(privateAddress)
    }

    public companion object {
        /**
         * Load an endpoint by its [publicAddress].
         */
        @Throws(PersistenceException::class)
        public suspend fun load(publicAddress: String): PublicThirdPartyEndpoint? =
            Storage.publicThirdParty.get(publicAddress)?.let {
                PublicThirdPartyEndpoint(it.publicAddress, it.identityCertificate)
            }

        /**
         * Import the public endpoint at [publicAddress].
         *
         * @param publicAddress The public address of the endpoint (e.g., `ping.awala.services`).
         * @param identityCertificateSerialized The DER serialization of identity certificate of the
         * endpoint.
         */
        @Throws(
            PersistenceException::class,
            InvalidThirdPartyEndpoint::class
        )
        public suspend fun import(
            publicAddress: String,
            identityCertificateSerialized: ByteArray
        ): PublicThirdPartyEndpoint {
            val identityCertificate = try {
                Certificate.deserialize(identityCertificateSerialized)
            } catch (exc: CertificateException) {
                throw InvalidThirdPartyEndpoint("Malformed identity certificate", exc)
            }
            try {
                identityCertificate.validate()
            } catch (exc: CertificateException) {
                throw InvalidThirdPartyEndpoint("Invalid identity certificate", exc)
            }
            val thirdPartyAddress = identityCertificate.subjectPrivateAddress
            Storage.publicThirdParty.set(
                thirdPartyAddress,
                PublicThirdPartyEndpointData(publicAddress, identityCertificate)
            )
            return PublicThirdPartyEndpoint(publicAddress, identityCertificate)
        }
    }
}

public class UnknownThirdPartyEndpointException(message: String) : AwaladroidException(message)
public class UnknownFirstPartyEndpointException(message: String) : AwaladroidException(message)
public class InvalidThirdPartyEndpoint(message: String, cause: Throwable? = null) :
    AwaladroidException(message, cause)

public class InvalidAuthorizationException(message: String, cause: Throwable) :
    AwaladroidException(message, cause)
