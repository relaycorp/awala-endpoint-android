package tech.relaycorp.awaladroid.endpoint

import java.nio.ByteBuffer
import java.security.PublicKey
import org.bson.BSONException
import org.bson.BsonBinary
import org.bson.BsonBinaryReader
import org.bson.BsonBinaryWriter
import org.bson.io.BasicOutputBuffer
import tech.relaycorp.awaladroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.pki.CertificationPath
import tech.relaycorp.relaynet.wrappers.deserializeRSAPublicKey

internal data class PrivateThirdPartyEndpointData(
    val identityKey: PublicKey,
    val pdaPath: CertificationPath
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
                    w.writeBinaryData("pda_path", BsonBinary(pdaPath.serialize()))
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
                    val pdaPathSerialized = r.readBinaryData("pda_path").data

                    r.readEndDocument()
                    PrivateThirdPartyEndpointData(
                        identityKey,
                        CertificationPath.deserialize(pdaPathSerialized)
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
