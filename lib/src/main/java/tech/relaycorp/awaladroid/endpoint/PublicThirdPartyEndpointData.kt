package tech.relaycorp.awaladroid.endpoint

import java.nio.ByteBuffer
import java.security.PublicKey
import org.bson.BSONException
import org.bson.BsonBinary
import org.bson.BsonBinaryReader
import org.bson.BsonBinaryWriter
import org.bson.io.BasicOutputBuffer
import tech.relaycorp.awaladroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.wrappers.deserializeRSAPublicKey

internal data class PublicThirdPartyEndpointData(
    val internetAddress: String,
    val identityKey: PublicKey,
) {
    @Throws(PersistenceException::class)
    fun serialize(): ByteArray =
        try {
            BasicOutputBuffer().use { output ->
                BsonBinaryWriter(output).use { w ->
                    w.writeStartDocument()
                    w.writeString("internet_address", internetAddress)
                    w.writeBinaryData(
                        "identity_key",
                        BsonBinary(identityKey.encoded)
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
                        r.readString("internet_address"),
                        r.readBinaryData("identity_key").data.deserializeRSAPublicKey()
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
