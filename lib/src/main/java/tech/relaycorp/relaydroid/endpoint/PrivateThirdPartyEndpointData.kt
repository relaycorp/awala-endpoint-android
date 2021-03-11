package tech.relaycorp.relaydroid.endpoint

import org.bson.AbstractBsonReader
import org.bson.BSONException
import org.bson.BsonBinary
import org.bson.BsonBinaryReader
import org.bson.BsonBinaryWriter
import org.bson.BsonType
import org.bson.io.BasicOutputBuffer
import tech.relaycorp.relaydroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import java.nio.ByteBuffer

internal data class PrivateThirdPartyEndpointData(
    val identityCertificate: Certificate,
    val authBundle: AuthorizationBundle
) {
    @Throws(PersistenceException::class)
    fun serialize(): ByteArray =
        try {
            BasicOutputBuffer().use { output ->
                BsonBinaryWriter(output).use { w ->
                    w.writeStartDocument()
                    w.writeBinaryData(
                        "identity_certificate",
                        BsonBinary(identityCertificate.serialize())
                    )
                    w.writeBinaryData("pda", BsonBinary(authBundle.pdaSerialized))
                    w.writeStartArray("pda_chain")
                    authBundle.pdaChainSerialized.forEach {
                        w.writeBinaryData(BsonBinary(it))
                    }
                    w.writeEndArray()
                    w.writeEndDocument()
                }
                output.toByteArray()
            }
        } catch (exp: BSONException) {
            throw PersistenceException("Could not serialize PrivateThirdPartyEndpoint", exp)
        }

    companion object {
        @Throws(PersistenceException::class)
        fun deserialize(byteArray: ByteArray): PrivateThirdPartyEndpointData =
            try {
                BsonBinaryReader(ByteBuffer.wrap(byteArray)).use { r ->
                    r.readStartDocument()

                    val identityCertificate = Certificate.deserialize(
                        r.readBinaryData("identity_certificate").data
                    )
                    val pdaSerialized = r.readBinaryData("pda").data

                    val pdaChainSerialized = mutableListOf<ByteArray>()
                    r.readName("pda_chain")
                    r.readStartArray()
                    while (r.readBsonType() != BsonType.END_OF_DOCUMENT) {
                        pdaChainSerialized.add(r.readBinaryData().data)
                    }
                    r.readEndArray()

                    r.readEndDocument()
                    PrivateThirdPartyEndpointData(
                        identityCertificate,
                        AuthorizationBundle(pdaSerialized, pdaChainSerialized)
                    )
                }
            } catch (exp: BSONException) {
                throw PersistenceException(
                    "Could not deserialize PrivateThirdPartyEndpoint",
                    exp
                )
            }
    }
}
