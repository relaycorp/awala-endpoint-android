package tech.relaycorp.relaydroid.storage.persistence

import tech.relaycorp.relaynet.RelaynetException

internal interface Persistence {

    @Throws(PersistenceException::class)
    suspend fun set(location: String, data: ByteArray)

    @Throws(PersistenceException::class)
    suspend fun get(location: String): ByteArray?

    @Throws(PersistenceException::class)
    suspend fun delete(location: String)

    suspend fun deleteAll(locationPrefix: String = "")

    suspend fun list(locationPrefix: String = ""): List<String>
}

public class PersistenceException(message: String, cause: Throwable? = null)
    : RelaynetException(message, cause)
