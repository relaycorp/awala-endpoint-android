package tech.relaycorp.relaydroid.endpoint

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
    public val thirdPartyAddress: String, // Private address
    public val identityCertificate: Certificate
) : Endpoint {

    public companion object {
        @Throws(PersistenceException::class)
        internal suspend fun load(
            firstPartyAddress: String, thirdPartyAddress: String
        ): ThirdPartyEndpoint? =
            PublicThirdPartyEndpoint.load(thirdPartyAddress)
                ?: PrivateThirdPartyEndpoint.load(firstPartyAddress, thirdPartyAddress)
    }
}

public class PrivateThirdPartyEndpoint(
    public val firstPartyAddress: String,
    thirdPartyAddress: String,
    public val authorization: Certificate,
    identityCertificate: Certificate
) : ThirdPartyEndpoint(thirdPartyAddress, identityCertificate) {

    override val address: String get() = thirdPartyAddress

    public companion object {

        @Throws(PersistenceException::class)
        public suspend fun load(
            firstPartyAddress: String, thirdPartyAddress: String
        ): PrivateThirdPartyEndpoint? {
            val key = "${firstPartyAddress}_$thirdPartyAddress"
            return Storage.thirdPartyAuthorization.get(key)?.let { auth ->
                Storage.thirdPartyIdentityCertificate.get(key)?.let { id ->
                    PrivateThirdPartyEndpoint(firstPartyAddress, thirdPartyAddress, auth, id)
                }
            }
        }

        @Throws(
            PersistenceException::class,
            UnknownFirstPartyEndpointException::class
        )
        public suspend fun importAuthorization(
            pda: Certificate, identity: Certificate
        ): PrivateThirdPartyEndpoint {
            val firstPartyAddress = pda.subjectPrivateAddress

            Storage.identityCertificate.get(firstPartyAddress)
                ?: throw UnknownFirstPartyEndpointException(
                    "First party endpoint $firstPartyAddress not registered"
                )

            try {
                pda.getCertificationPath(emptyList(), listOf(identity))
            } catch (e: CertificateException) {
                throw InvalidAuthorizationException("PDA was not issued by third-party endpoint", e)
            }

            val thirdPartyAddress = identity.subjectPrivateAddress

            val key = "${firstPartyAddress}_$thirdPartyAddress"
            Storage.thirdPartyAuthorization.set(key, pda)
            Storage.thirdPartyIdentityCertificate.set(key, identity)

            return PrivateThirdPartyEndpoint(firstPartyAddress, thirdPartyAddress, pda, identity)
        }
    }
}

public class PublicThirdPartyEndpoint(
    public val publicAddress: String,
    thirdPartyAddress: String,
    identityCertificate: Certificate
) : ThirdPartyEndpoint(thirdPartyAddress, identityCertificate) {

    override val address: String get() = "https://$publicAddress"

    public companion object {
        @Throws(PersistenceException::class)
        public suspend fun load(thirdPartyAddress: String): PublicThirdPartyEndpoint? =
            Storage.publicThirdPartyCertificate.get(thirdPartyAddress)?.let {
                PublicThirdPartyEndpoint(it.publicAddress, thirdPartyAddress, it.identityCertificate)
            }

        @Throws(
            PersistenceException::class,
            CertificateException::class
        )
        public suspend fun import(publicAddress: String, certificate: Certificate): PublicThirdPartyEndpoint {
            certificate.validate()
            val thirdPartyAddress = certificate.subjectPrivateAddress
            Storage.publicThirdPartyCertificate.set(thirdPartyAddress, StoredData(publicAddress, certificate))
            return PublicThirdPartyEndpoint(publicAddress, thirdPartyAddress, certificate)
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
            } catch (exp: Exception) {
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
                } catch (exp: Exception) {
                    throw PersistenceException("Could not deserialize PublicThirdPartyEndpoint", exp)
                }
        }
    }
}

public class UnknownThirdPartyEndpointException(message: String) : RelaynetException(message, null)
public class UnknownFirstPartyEndpointException(message: String) : RelaynetException(message, null)
public class InvalidAuthorizationException(message: String, cause: Throwable)
    : RelaynetException(message, cause)
