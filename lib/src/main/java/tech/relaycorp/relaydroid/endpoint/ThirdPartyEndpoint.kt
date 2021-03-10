package tech.relaycorp.relaydroid.endpoint

import java.nio.ByteBuffer
import org.bson.BSONException
import org.bson.BsonBinary
import org.bson.BsonBinaryReader
import org.bson.BsonBinaryWriter
import org.bson.io.BasicOutputBuffer
import tech.relaycorp.relaydroid.RelaydroidException
import tech.relaycorp.relaydroid.Storage
import tech.relaycorp.relaydroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import tech.relaycorp.relaynet.wrappers.x509.CertificateException

/**
 * An endpoint owned by a different instance of this app, or a different app in the same service.
 */
public sealed class ThirdPartyEndpoint(
    internal val identityCertificate: Certificate
) : Endpoint {

    /**
     * The private address of the endpoint.
     */
    public val privateAddress: String get() = identityCertificate.subjectPrivateAddress

    internal companion object {
        @Throws(PersistenceException::class)
        internal suspend fun load(
            firstPartyAddress: String,
            thirdPartyPrivateAddress: String
        ): ThirdPartyEndpoint? =
            PublicThirdPartyEndpoint.load(thirdPartyPrivateAddress)
                ?: PrivateThirdPartyEndpoint.load(firstPartyAddress, thirdPartyPrivateAddress)
    }
}

/**
 * A private third-party endpoint (i.e., one behind a different private gateway).
 */
public class PrivateThirdPartyEndpoint internal constructor(
    public val firstPartyAddress: String,
    internal val pda: Certificate,
    identityCertificate: Certificate
) : ThirdPartyEndpoint(identityCertificate) {

    override val address: String get() = privateAddress

    public companion object {
        /**
         * Load an endpoint.
         */
        @Throws(PersistenceException::class)
        public suspend fun load(
            firstPartyAddress: String,
            thirdPartyAddress: String
        ): PrivateThirdPartyEndpoint? {
            val key = "${firstPartyAddress}_$thirdPartyAddress"
            return Storage.thirdPartyAuthorization.get(key)?.let { auth ->
                Storage.thirdPartyIdentityCertificate.get(key)?.let { id ->
                    PrivateThirdPartyEndpoint(firstPartyAddress, auth, id)
                }
            }
        }

        /**
         * Import PDA along with its chain.
         */
        @Throws(
            PersistenceException::class,
            UnknownFirstPartyEndpointException::class,
            InvalidAuthorizationException::class
        )
        public suspend fun import(
            pda: Certificate,
            identityCertificate: Certificate
        ): PrivateThirdPartyEndpoint {
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
                pda.getCertificationPath(emptyList(), listOf(identityCertificate))
            } catch (e: CertificateException) {
                throw InvalidAuthorizationException("PDA was not issued by third-party endpoint", e)
            }

            val thirdPartyAddress = identityCertificate.subjectPrivateAddress

            val key = "${firstPartyAddress}_$thirdPartyAddress"
            Storage.thirdPartyAuthorization.set(key, pda)
            Storage.thirdPartyIdentityCertificate.set(key, identityCertificate)

            return PrivateThirdPartyEndpoint(firstPartyAddress, pda, identityCertificate)
        }
    }
}

/**
 * A public third-party endpoint (i.e., an Internet host in a centralized service).
 */
public class PublicThirdPartyEndpoint internal constructor(
    public val publicAddress: String,
    identityCertificate: Certificate
) : ThirdPartyEndpoint(identityCertificate) {

    override val address: String get() = "https://$publicAddress"

    public companion object {
        /**
         * Load an endpoint by its [publicAddress].
         */
        @Throws(PersistenceException::class)
        public suspend fun load(publicAddress: String): PublicThirdPartyEndpoint? =
            Storage.publicThirdPartyCertificate.get(publicAddress)?.let {
                PublicThirdPartyEndpoint(it.publicAddress, it.identityCertificate)
            }

        /**
         * Import the public endpoint at [publicAddress].
         *
         * @param publicAddress The public address of the endpoint (e.g., `ping.awala.services`).
         * @param identityCertificate The identity certificate of the endpoint.
         */
        @Throws(
            PersistenceException::class,
            InvalidThirdPartyEndpoint::class
        )
        public suspend fun import(
            publicAddress: String,
            identityCertificate: Certificate
        ): PublicThirdPartyEndpoint {
            try {
                identityCertificate.validate()
            } catch (exc: CertificateException) {
                throw InvalidThirdPartyEndpoint("Invalid identity certificate")
            }
            val thirdPartyAddress = identityCertificate.subjectPrivateAddress
            Storage.publicThirdPartyCertificate.set(
                thirdPartyAddress,
                StoredData(publicAddress, identityCertificate)
            )
            return PublicThirdPartyEndpoint(publicAddress, identityCertificate)
        }
    }

    internal data class StoredData(
        val publicAddress: String,
        val identityCertificate: Certificate
    ) {
        @Throws(PersistenceException::class)
        fun serialize(): ByteArray {
            try {
                val output = BasicOutputBuffer()
                BsonBinaryWriter(output).use {
                    it.writeStartDocument()
                    it.writeString("public_address", publicAddress)
                    it.writeBinaryData(
                        "identity_certificate",
                        BsonBinary(identityCertificate.serialize())
                    )
                    it.writeEndDocument()
                }
                return output.toByteArray()
            } catch (exp: BSONException) {
                throw PersistenceException("Could not serialize PublicThirdPartyEndpoint", exp)
            }
        }

        companion object {
            @Throws(PersistenceException::class)
            fun deserialize(byteArray: ByteArray): StoredData =
                try {
                    BsonBinaryReader(ByteBuffer.wrap(byteArray)).use { reader ->
                        reader.readStartDocument()
                        StoredData(
                            reader.readString("public_address"),
                            Certificate.deserialize(
                                reader.readBinaryData("identity_certificate").data
                            )
                        ).also {
                            reader.readEndDocument()
                        }
                    }
                } catch (exp: BSONException) {
                    throw PersistenceException(
                        "Could not deserialize PublicThirdPartyEndpoint",
                        exp
                    )
                }
        }
    }
}

public class UnknownThirdPartyEndpointException(message: String) : RelaydroidException(message)
public class UnknownFirstPartyEndpointException(message: String) : RelaydroidException(message)
public class InvalidThirdPartyEndpoint(message: String) : RelaydroidException(message)
public class InvalidAuthorizationException(message: String, cause: Throwable) :
    RelaydroidException(message, cause)
