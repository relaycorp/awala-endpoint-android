package tech.relaycorp.awaladroid.endpoint

import java.nio.ByteBuffer
import java.security.PublicKey
import org.bson.BSONException
import org.bson.BsonBinary
import org.bson.BsonBinaryReader
import org.bson.BsonBinaryWriter
import org.bson.BsonType
import org.bson.io.BasicOutputBuffer
import tech.relaycorp.awaladroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.wrappers.deserializeRSAPublicKey

internal data class PrivateThirdPartyEndpointData(
    val identityKey: PublicKey,
    val authBundle: AuthorizationBundle
) {
    @Throws(PersistenceException::class)
    fun serialize(): ByteArray =
        try {
            BasicOutputBuffer().use { output ->
                BsonBinaryWriter(output).use { w ->
                    w.writeStartDocument()
                    w.writeBinaryData(
                        "identity_key",
                        BsonBinary(identityKey.encoded)
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

                    val identityKey =
                        r.readBinaryData("identity_key").data.deserializeRSAPublicKey()
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
                        identityKey,
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
