package tech.relaycorp.awaladroid.endpoint

import org.bson.BSONException
import org.bson.BsonBinary
import org.bson.BsonBinaryReader
import org.bson.BsonBinaryWriter
import org.bson.io.BasicOutputBuffer
import tech.relaycorp.awaladroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.pki.CertificationPath
import tech.relaycorp.relaynet.wrappers.deserializeRSAPublicKey
import java.nio.ByteBuffer
import java.security.PublicKey

internal data class PrivateThirdPartyEndpointData(
    val identityKey: PublicKey,
    val pdaPath: CertificationPath,
    val internetGatewayAddress: String,
) {
    @Throws(PersistenceException::class)
    fun serialize(): ByteArray =
        try {
            BasicOutputBuffer().use { output ->
                BsonBinaryWriter(output).use { w ->
                    w.writeStartDocument()
                    w.writeBinaryData(
                        "identity_key",
                        BsonBinary(identityKey.encoded),
                    )
                    w.writeBinaryData("pda_path", BsonBinary(pdaPath.serialize()))
                    w.writeString("internet_address", internetGatewayAddress)
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
                    val internetGatewayAddress = r.readString("internet_address")

                    r.readEndDocument()
                    PrivateThirdPartyEndpointData(
                        identityKey,
                        CertificationPath.deserialize(pdaPathSerialized),
                        internetGatewayAddress,
                    )
                }
            } catch (exp: BSONException) {
                throw PersistenceException(
                    "Could not deserialize PrivateThirdPartyEndpoint",
                    exp,
                )
            }
    }
}
