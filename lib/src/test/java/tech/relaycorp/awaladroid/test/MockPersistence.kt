package tech.relaycorp.awaladroid.test

import tech.relaycorp.awaladroid.storage.persistence.Persistence

internal class MockPersistence : Persistence {
    private val values: MutableMap<String, ByteArray> = mutableMapOf()

    override suspend fun set(
        location: String,
        data: ByteArray,
    ) {
        values[location] = data
    }

    override suspend fun get(location: String) = values[location]

    override suspend fun delete(location: String) {
        values.remove(location)
    }

    override suspend fun deleteAll(locationPrefix: String) {
        list(locationPrefix).map { delete(it) }
    }

    override suspend fun list(locationPrefix: String) =
        values.keys.filter { it.startsWith(locationPrefix) }

    fun reset() = values.clear()
}
