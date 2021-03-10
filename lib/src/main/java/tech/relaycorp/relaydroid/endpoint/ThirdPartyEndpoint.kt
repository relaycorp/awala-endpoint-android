package tech.relaycorp.relaydroid.endpoint

import org.bson.BSONException
import org.bson.BsonBinary
import org.bson.BsonBinaryReader
import org.bson.BsonBinaryWriter
import org.bson.io.BasicOutputBuffer
import tech.relaycorp.relaydroid.Storage
import tech.relaycorp.relaydroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.RelaynetException
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import tech.relaycorp.relaynet.wrappers.x509.CertificateException
import java.nio.ByteBuffer


public sealed class ThirdPartyEndpoint(
    public val identityCertificate: Certificate
) : Endpoint {

    public val privateAddress : String get() = identityCertificate.subjectPrivateAddress

    internal companion object {
        @Throws(PersistenceException::class)
        internal suspend fun load(
            firstPartyAddress: String, thirdPartyPrivateAddress: String
        ): ThirdPartyEndpoint? =
            PublicThirdPartyEndpoint.load(thirdPartyPrivateAddress)
                ?: PrivateThirdPartyEndpoint.load(firstPartyAddress, thirdPartyPrivateAddress)
    }
}

public class PrivateThirdPartyEndpoint internal constructor(
    public val firstPartyAddress: String,
    public val authorization: Certificate,
    identityCertificate: Certificate
) : ThirdPartyEndpoint(identityCertificate) {

    override val address: String get() = privateAddress

    public companion object {

        @Throws(PersistenceException::class)
        public suspend fun load(
            firstPartyAddress: String, thirdPartyAddress: String
        ): PrivateThirdPartyEndpoint? {
            val key = "${firstPartyAddress}_$thirdPartyAddress"
            return Storage.thirdPartyAuthorization.get(key)?.let { auth ->
                Storage.thirdPartyIdentityCertificate.get(key)?.let { id ->
                    PrivateThirdPartyEndpoint(firstPartyAddress, auth, id)
                }
            }
        }

        @Throws(
            PersistenceException::class,
            UnknownFirstPartyEndpointException::class
        )
        public suspend fun importAuthorization(
            pda: Certificate, identityCertificate: Certificate
        ): PrivateThirdPartyEndpoint {
            val firstPartyAddress = pda.subjectPrivateAddress

            Storage.identityCertificate.get(firstPartyAddress)
                ?: throw UnknownFirstPartyEndpointException(
                    "First party endpoint $firstPartyAddress not registered"
                )

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

public class PublicThirdPartyEndpoint internal constructor(
    public val publicAddress: String,
    identityCertificate: Certificate
) : ThirdPartyEndpoint(identityCertificate) {

    override val address: String get() = "https://$publicAddress"

    public companion object {
        @Throws(PersistenceException::class)
        public suspend fun load(publicAddress: String): PublicThirdPartyEndpoint? =
            Storage.publicThirdPartyCertificate.get(publicAddress)?.let {
                PublicThirdPartyEndpoint(it.publicAddress, it.identityCertificate)
            }

        @Throws(
            PersistenceException::class,
            CertificateException::class
        )
        public suspend fun import(
            publicAddress: String,
            identityCertificate: Certificate
        ): PublicThirdPartyEndpoint {
            identityCertificate.validate()
            val thirdPartyAddress = identityCertificate.subjectPrivateAddress
            Storage.publicThirdPartyCertificate.set(
                thirdPartyAddress,
                StoredData(publicAddress, identityCertificate)
            )
            return PublicThirdPartyEndpoint(publicAddress, identityCertificate)
        }
    }

    internal data class StoredData(
        val publicAddress: String, val identityCertificate: Certificate
    ) {
        @Throws(PersistenceException::class)
        fun serialize(): ByteArray {
            try {
                val output = BasicOutputBuffer()
                BsonBinaryWriter(output).use {
                    it.writeStartDocument()
                    it.writeString("public_address", publicAddress)
                    it.writeBinaryData("identity_certificate", BsonBinary(identityCertificate.serialize()))
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
                    throw PersistenceException("Could not deserialize PublicThirdPartyEndpoint", exp)
                }
        }
    }
}

public class UnknownThirdPartyEndpointException(message: String) : RelaynetException(message, null)
public class UnknownFirstPartyEndpointException(message: String) : RelaynetException(message, null)
public class InvalidAuthorizationException(message: String, cause: Throwable)
    : RelaynetException(message, cause)
