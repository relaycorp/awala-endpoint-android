package tech.relaycorp.awaladroid.endpoint

import tech.relaycorp.awaladroid.Awala
import tech.relaycorp.awaladroid.AwaladroidException
import tech.relaycorp.awaladroid.SetupPendingException
import tech.relaycorp.awaladroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.InvalidNodeConnectionParams
import tech.relaycorp.relaynet.NodeConnectionParams
import tech.relaycorp.relaynet.PrivateEndpointConnParams
import tech.relaycorp.relaynet.keystores.MissingKeyException
import tech.relaycorp.relaynet.messages.Recipient
import tech.relaycorp.relaynet.pki.CertificationPathException
import tech.relaycorp.relaynet.wrappers.nodeId
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import java.security.PublicKey

/**
 * An endpoint owned by a different instance of this app, or a different app in the same service.
 */
public sealed class ThirdPartyEndpoint(
    internal val identityKey: PublicKey,
    public val internetAddress: String,
) : Endpoint(identityKey.nodeId) {
    internal val recipient: Recipient
        get() = Recipient(nodeId, internetAddress)

    /**
     * Delete the endpoint.
     */
    @Throws(PersistenceException::class)
    public open suspend fun delete(linkedFirstPartyEndpoint: FirstPartyEndpoint) {
        val context = Awala.getContextOrThrow()
        context.privateKeyStore.deleteBoundSessionKeys(linkedFirstPartyEndpoint.nodeId, nodeId)
        context.sessionPublicKeyStore.delete(linkedFirstPartyEndpoint.nodeId, nodeId)
        context.channelManager.delete(linkedFirstPartyEndpoint, this)
    }

    internal companion object {
        @Throws(PersistenceException::class)
        internal suspend fun load(
            firstPartyAddress: String,
            thirdPartyId: String,
        ): ThirdPartyEndpoint? =
            PublicThirdPartyEndpoint.load(thirdPartyId)
                ?: PrivateThirdPartyEndpoint.load(thirdPartyId, firstPartyAddress)
    }
}

/**
 * A private third-party endpoint (i.e., one behind a different private gateway).
 *
 * @property firstPartyEndpointAddress The nodeId of the first-party endpoint linked to
 * this endpoint.
 */
public class PrivateThirdPartyEndpoint internal constructor(
    public val firstPartyEndpointAddress: String,
    identityKey: PublicKey,
    internal val pda: Certificate,
    internal val pdaChain: List<Certificate>,
    internetAddress: String,
) : ThirdPartyEndpoint(identityKey, internetAddress) {
    private val storageKey = "${firstPartyEndpointAddress}_$nodeId"

    @Throws(PersistenceException::class, SetupPendingException::class)
    override suspend fun delete(linkedFirstPartyEndpoint: FirstPartyEndpoint) {
        val context = Awala.getContextOrThrow()
        context.storage.privateThirdParty.delete(storageKey)
        super.delete(linkedFirstPartyEndpoint)
    }

    @Throws(InvalidAuthorizationException::class)
    internal suspend fun updateParams(connectionParams: PrivateEndpointConnParams) {
        val deliveryAuth = connectionParams.deliveryAuth
        try {
            deliveryAuth.validate()
        } catch (exc: CertificationPathException) {
            throw InvalidAuthorizationException("PDA path is invalid", exc)
        }

        val pdaSubjectAddress = deliveryAuth.leafCertificate.subjectId
        if (pdaSubjectAddress != firstPartyEndpointAddress) {
            throw InvalidAuthorizationException(
                "PDA subject ($pdaSubjectAddress) is not first-party endpoint",
            )
        }

        val pdaIssuerAddress = deliveryAuth.certificateAuthorities.first().subjectId
        if (pdaIssuerAddress != nodeId) {
            throw InvalidAuthorizationException(
                "PDA issuer ($pdaIssuerAddress) is not third-party endpoint",
            )
        }

        val context = Awala.getContextOrThrow()
        val data =
            PrivateThirdPartyEndpointData(
                identityKey,
                deliveryAuth,
                connectionParams.internetGatewayAddress,
            )
        context.storage.privateThirdParty.set(storageKey, data)
    }

    public companion object {
        /**
         * Load an endpoint.
         */
        @Throws(PersistenceException::class, SetupPendingException::class)
        public suspend fun load(
            thirdPartyAddress: String,
            firstPartyAddress: String,
        ): PrivateThirdPartyEndpoint? {
            val key = "${firstPartyAddress}_$thirdPartyAddress"
            val storage = Awala.getContextOrThrow().storage
            return storage.privateThirdParty.get(key)?.let { data ->
                PrivateThirdPartyEndpoint(
                    firstPartyAddress,
                    data.identityKey,
                    data.pdaPath.leafCertificate,
                    data.pdaPath.certificateAuthorities,
                    data.internetGatewayAddress,
                )
            }
        }

        /**
         * Create private third-party endpoint by importing its PDA and chain.
         */
        @Throws(
            PersistenceException::class,
            UnknownFirstPartyEndpointException::class,
            InvalidAuthorizationException::class,
            InvalidThirdPartyEndpoint::class,
            SetupPendingException::class,
        )
        public suspend fun import(
            connectionParamsSerialized: ByteArray,
            firstPartyEndpoint: FirstPartyEndpoint,
        ): PrivateThirdPartyEndpoint {
            val context = Awala.getContextOrThrow()

            val params =
                try {
                    PrivateEndpointConnParams.deserialize(connectionParamsSerialized)
                } catch (exc: InvalidNodeConnectionParams) {
                    throw InvalidThirdPartyEndpoint("Malformed connection params", exc)
                }
            val pdaPath = params.deliveryAuth
            val pda = pdaPath.leafCertificate
            val pdaChain = pdaPath.certificateAuthorities

            val firstPartyAddress = pda.subjectId
            try {
                context.privateKeyStore.retrieveIdentityKey(firstPartyAddress)
            } catch (exc: MissingKeyException) {
                throw UnknownFirstPartyEndpointException(
                    "First-party endpoint $firstPartyAddress is not registered",
                )
            }

            try {
                pdaPath.validate()
            } catch (exc: CertificationPathException) {
                throw InvalidAuthorizationException("PDA path is invalid", exc)
            }

            val endpoint =
                PrivateThirdPartyEndpoint(
                    firstPartyAddress,
                    params.identityKey,
                    pda,
                    pdaChain,
                    params.internetGatewayAddress,
                )

            val data =
                PrivateThirdPartyEndpointData(
                    params.identityKey,
                    pdaPath,
                    params.internetGatewayAddress,
                )
            context.storage.privateThirdParty.set(endpoint.storageKey, data)

            context.sessionPublicKeyStore.save(
                params.sessionKey,
                firstPartyEndpoint.nodeId,
                endpoint.nodeId,
            )

            return endpoint
        }
    }
}

/**
 * A public third-party endpoint (i.e., an Internet host in a centralized service).
 *
 * @property internetAddress The public address of the endpoint (e.g., "ping.awala.services").
 */
public class PublicThirdPartyEndpoint internal constructor(
    internetAddress: String,
    identityKey: PublicKey,
) : ThirdPartyEndpoint(identityKey, internetAddress) {
    @Throws(PersistenceException::class, SetupPendingException::class)
    override suspend fun delete(linkedFirstPartyEndpoint: FirstPartyEndpoint) {
        val context = Awala.getContextOrThrow()
        context.storage.publicThirdParty.delete(nodeId)
        super.delete(linkedFirstPartyEndpoint)
    }

    public companion object {
        /**
         * Load an endpoint by its [nodeId].
         */
        @Throws(PersistenceException::class, SetupPendingException::class)
        public suspend fun load(nodeId: String): PublicThirdPartyEndpoint? {
            val storage = Awala.getContextOrThrow().storage
            return storage.publicThirdParty.get(nodeId)?.let {
                PublicThirdPartyEndpoint(it.internetAddress, it.identityKey)
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
            connectionParamsSerialized: ByteArray,
            firstPartyEndpoint: FirstPartyEndpoint,
        ): PublicThirdPartyEndpoint {
            val context = Awala.getContextOrThrow()
            val connectionParams =
                try {
                    NodeConnectionParams.deserialize(connectionParamsSerialized)
                } catch (exc: InvalidNodeConnectionParams) {
                    throw InvalidThirdPartyEndpoint(
                        "Connection params serialization is malformed",
                        exc,
                    )
                }
            val peerNodeId = connectionParams.identityKey.nodeId
            context.storage.publicThirdParty.set(
                peerNodeId,
                PublicThirdPartyEndpointData(
                    connectionParams.internetAddress,
                    connectionParams.identityKey,
                ),
            )
            context.sessionPublicKeyStore.save(
                connectionParams.sessionKey,
                firstPartyEndpoint.nodeId,
                peerNodeId,
            )
            return PublicThirdPartyEndpoint(
                connectionParams.internetAddress,
                connectionParams.identityKey,
            )
        }
    }
}

public class UnknownThirdPartyEndpointException(message: String) : AwaladroidException(message)

public class UnknownFirstPartyEndpointException(message: String) : AwaladroidException(message)

public class InvalidThirdPartyEndpoint(message: String, cause: Throwable? = null) :
    AwaladroidException(message, cause)

public class InvalidAuthorizationException(message: String, cause: Throwable? = null) :
    AwaladroidException(message, cause)
