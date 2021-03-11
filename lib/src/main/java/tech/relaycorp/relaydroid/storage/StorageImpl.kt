package tech.relaycorp.relaydroid.storage

import java.security.KeyPair
import tech.relaycorp.relaydroid.endpoint.PrivateThirdPartyEndpointData
import tech.relaycorp.relaydroid.endpoint.PublicThirdPartyEndpointData
import tech.relaycorp.relaydroid.storage.persistence.Persistence
import tech.relaycorp.relaydroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.wrappers.deserializeRSAKeyPair
import tech.relaycorp.relaynet.wrappers.x509.Certificate

// TODO: Test
internal class StorageImpl
constructor(
    persistence: Persistence
) {

    internal val identityKeyPair: Module<KeyPair> = Module(
        persistence = persistence,
        prefix = "id_key_pair_",
        serializer = { it.private.encoded },
        deserializer = ByteArray::deserializeRSAKeyPair
    )

    internal val identityCertificate: Module<Certificate> = Module(
        persistence = persistence,
        prefix = "id_certificate_",
        serializer = { it.serialize() },
        deserializer = { Certificate.deserialize(it) }
    )

    internal val gatewayCertificate: SingleModule<Certificate> = SingleModule(
        persistence = persistence,
        prefix = "gateway_certificate_",
        serializer = Certificate::serialize,
        deserializer = Certificate::deserialize
    )

    internal val publicThirdParty: Module<PublicThirdPartyEndpointData> = Module(
        persistence = persistence,
        prefix = "public_third_party_",
        serializer = PublicThirdPartyEndpointData::serialize,
        deserializer = PublicThirdPartyEndpointData::deserialize
    )

    internal val privateThirdParty: Module<PrivateThirdPartyEndpointData> = Module(
        persistence = persistence,
        prefix = "private_third_party_",
        serializer = PrivateThirdPartyEndpointData::serialize,
        deserializer = PrivateThirdPartyEndpointData::deserialize
    )

    internal open class Module<T>(
        private val persistence: Persistence,
        private val prefix: String,
        private val serializer: (T) -> ByteArray,
        private val deserializer: (ByteArray) -> T
    ) {

        @Throws(PersistenceException::class)
        suspend fun set(key: String, data: T) {
            persistence.set("$prefix$key", serializer(data))
        }

        @Throws(PersistenceException::class)
        suspend fun get(key: String): T? =
            persistence.get("$prefix$key")?.let { deserializer(it) }

        @Throws(PersistenceException::class)
        suspend fun delete(key: String) {
            persistence.delete("$prefix$key")
        }

        suspend fun deleteAll() {
            persistence.deleteAll(prefix)
        }

        suspend fun list(): List<String> =
            persistence.list(prefix)
                .map { it.substring(prefix.length) }
    }

    internal class SingleModule<T>(
        persistence: Persistence,
        prefix: String,
        serializer: (T) -> ByteArray,
        deserializer: (ByteArray) -> T
    ) : Module<T>(persistence, prefix, serializer, deserializer) {

        @Throws(PersistenceException::class)
        suspend fun get() = get("base")

        @Throws(PersistenceException::class)
        suspend fun set(data: T) = set("base", data)
    }
}
