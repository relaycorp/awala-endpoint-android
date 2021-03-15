package tech.relaycorp.awaladroid.endpoint

import java.nio.ByteBuffer
import org.bson.BSONException
import org.bson.BsonBinary
import org.bson.BsonBinaryReader
import org.bson.BsonBinaryWriter
import org.bson.io.BasicOutputBuffer
import tech.relaycorp.awaladroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.wrappers.x509.Certificate

internal data class PublicThirdPartyEndpointData(
    val publicAddress: String,
    val identityCertificate: Certificate
) {
    @Throws(PersistenceException::class)
    fun serialize(): ByteArray =
        try {
            BasicOutputBuffer().use { output ->
                BsonBinaryWriter(output).use { w ->
                    w.writeStartDocument()
                    w.writeString("public_address", publicAddress)
                    w.writeBinaryData(
                        "identity_certificate",
                        BsonBinary(identityCertificate.serialize())
                    )
                    w.writeEndDocument()
                }
                output.toByteArray()
            }
        } catch (exp: BSONException) {
            throw PersistenceException("Could not serialize PublicThirdPartyEndpoint", exp)
        }

    companion object {
        @Throws(PersistenceException::class)
        fun deserialize(byteArray: ByteArray): PublicThirdPartyEndpointData =
            try {
                BsonBinaryReader(ByteBuffer.wrap(byteArray)).use { r ->
                    r.readStartDocument()
                    PublicThirdPartyEndpointData(
                        r.readString("public_address"),
                        Certificate.deserialize(
                            r.readBinaryData("identity_certificate").data
                        )
                    ).also {
                        r.readEndDocument()
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
