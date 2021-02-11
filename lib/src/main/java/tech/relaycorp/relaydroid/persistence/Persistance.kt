package tech.relaycorp.relaydroid.persistence

internal interface Persistence {
    suspend fun set(location: String, data: ByteArray)
    suspend fun get(location: String): ByteArray?
    suspend fun delete(location: String)
    suspend fun deleteAll()
    suspend fun list(locationPrefix: String = ""): List<String>
}

public class PersistenceException(message: String, cause: Throwable? = null)
    : Exception(message, cause)
