package tech.relaycorp.awaladroid.storage.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.CoroutineContext

internal class DiskPersistence(
    private val fileDir: String,
    private val coroutineContext: CoroutineContext = Dispatchers.IO,
    private val rootFolder: String = "awaladroid",
) : Persistence {
    @Suppress("BlockingMethodInNonBlockingContext")
    @Throws(PersistenceException::class)
    override suspend fun set(
        location: String,
        data: ByteArray,
    ) {
        withContext(coroutineContext) {
            deleteIfExists(location)
            try {
                buildFile(location)
                    .outputStream()
                    .use { it.write(data) }
            } catch (exception: IOException) {
                throw PersistenceException("Failed to write to file at $location", exception)
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    @Throws(PersistenceException::class)
    override suspend fun get(location: String): ByteArray? =
        withContext(coroutineContext) {
            try {
                buildFile(location)
                    .inputStream()
                    .use { it.readBytes() }
            } catch (exception: IOException) {
                if (buildFile(location).exists()) {
                    throw PersistenceException("Failed to read file at $location", exception)
                }
                null
            }
        }

    @Throws(PersistenceException::class)
    override suspend fun delete(location: String) {
        withContext(coroutineContext) {
            val result = buildFile(location).delete()
            if (!result) throw PersistenceException("Failed to delete file at $location")
        }
    }

    private fun deleteIfExists(location: String) {
        buildFile(location).delete()
    }

    override suspend fun deleteAll(locationPrefix: String) {
        withContext(coroutineContext) {
            val parentFolder = buildFile("")
            parentFolder
                .listFiles { file: File -> file.name.startsWith(locationPrefix) }
                ?.forEach { it.delete() }
        }
    }

    override suspend fun list(locationPrefix: String) =
        withContext(coroutineContext) {
            val rootFolder = buildFile("")
            rootFolder
                .walkTopDown()
                .toList()
                .let { it.subList(1, it.size) } // skip first, the root
                .map { it.absolutePath.replace(rootFolder.absolutePath + File.separator, "") }
                .filter { it.startsWith(locationPrefix) }
        }

    // Helpers

    private fun buildFile(location: String) =
        File(fileDir, "$rootFolder${File.separator}$location").also {
            it.parentFile?.mkdirs()
        }
}
