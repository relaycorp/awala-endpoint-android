package tech.relaycorp.awaladroid.storage.persistence

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class EncryptedDiskPersistence(
    private val context: Context,
    private val coroutineContext: CoroutineContext = Dispatchers.IO,
    private val rootFolder: String = "awaladroid"
) : Persistence {

    @Suppress("BlockingMethodInNonBlockingContext")
    @Throws(PersistenceException::class)
    override suspend fun set(location: String, data: ByteArray) {
        withContext(coroutineContext) {
            deleteIfExists(location)
            try {
                buildEncryptedFile(location)
                    .openFileOutput()
                    .use { it.write(data) }
            } catch (exception: IOException) {
                throw PersistenceException("Failed to write to file at $location", exception)
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    @Throws(PersistenceException::class)
    override suspend fun get(location: String): ByteArray? = withContext(coroutineContext) {
        try {
            buildEncryptedFile(location)
                .openFileInput()
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

    override suspend fun list(locationPrefix: String) = withContext(coroutineContext) {
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
        File(context.filesDir, "$rootFolder${File.separator}$location").also {
            it.parentFile?.mkdirs()
        }

    private fun buildEncryptedFile(location: String) =
        EncryptedFile.Builder(
            context,
            buildFile(location),
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

    private val masterKey by lazy {
        MasterKey.Builder(context, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    companion object {
        private const val MASTER_KEY_ALIAS = "_relaydroid_master_key_"
    }
}
