package tech.relaycorp.awaladroid.storage

import androidx.annotation.VisibleForTesting
import tech.relaycorp.awaladroid.endpoint.PrivateThirdPartyEndpointData
import tech.relaycorp.awaladroid.endpoint.PublicThirdPartyEndpointData
import tech.relaycorp.awaladroid.storage.persistence.Persistence
import tech.relaycorp.awaladroid.storage.persistence.PersistenceException
import java.nio.charset.Charset

// TODO: Test
internal class StorageImpl
constructor(
    persistence: Persistence,
) {

    private val ascii = Charset.forName("ASCII")
    internal val gatewayId: SingleModule<String> = SingleModule(
        persistence = persistence,
        prefix = "gateway_id_",
        serializer = { address: String -> address.toByteArray(ascii) },
        deserializer = { addressSerialized: ByteArray -> addressSerialized.toString(ascii) },
    )

    internal val internetAddress: SingleModule<String> = SingleModule(
        persistence = persistence,
        prefix = "internet_address_",
        serializer = { internetAddress: String -> internetAddress.toByteArray(ascii) },
        deserializer = { internetAddressSerialized: ByteArray ->
            internetAddressSerialized.toString(ascii)
        },
    )

    internal val publicThirdParty: Module<PublicThirdPartyEndpointData> = Module(
        persistence = persistence,
        prefix = "public_third_party_",
        serializer = PublicThirdPartyEndpointData::serialize,
        deserializer = PublicThirdPartyEndpointData::deserialize,
    )

    internal val privateThirdParty: Module<PrivateThirdPartyEndpointData> = Module(
        persistence = persistence,
        prefix = "private_third_party_",
        serializer = PrivateThirdPartyEndpointData::serialize,
        deserializer = PrivateThirdPartyEndpointData::deserialize,
    )

    internal open class Module<T>(
        private val persistence: Persistence,
        @get:VisibleForTesting
        internal val prefix: String,
        private val serializer: (T) -> ByteArray,
        private val deserializer: (ByteArray) -> T,
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
        deserializer: (ByteArray) -> T,
    ) : Module<T>(persistence, prefix, serializer, deserializer) {

        @Throws(PersistenceException::class)
        suspend fun get() = get("base")

        @Throws(PersistenceException::class)
        suspend fun set(data: T) = set("base", data)
    }
}
