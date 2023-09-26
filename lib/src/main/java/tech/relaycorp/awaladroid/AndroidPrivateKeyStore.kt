package tech.relaycorp.awaladroid

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import tech.relaycorp.awala.keystores.file.FileKeystoreRoot
import tech.relaycorp.awala.keystores.file.FilePrivateKeyStore
import java.io.File
import javax.crypto.AEADBadTagException

internal class AndroidPrivateKeyStore(
    root: FileKeystoreRoot,
    private val context: Context,
) : FilePrivateKeyStore(root) {

    @Throws(EncryptionInitializationException::class)
    override fun makeEncryptedInputStream(file: File) = buildEncryptedFile(file).openFileInput()

    @Throws(EncryptionInitializationException::class)
    override fun makeEncryptedOutputStream(file: File) = buildEncryptedFile(file).openFileOutput()

    @Throws(EncryptionInitializationException::class)
    private fun buildEncryptedFile(file: File) =
        try {
            EncryptedFile.Builder(
                context,
                file,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
            ).build()
        } catch (e: AEADBadTagException) {
            // Known issue: https://issuetracker.google.com/issues/164901843
            throw EncryptionInitializationException(e)
        }

    private val masterKey by lazy {
        MasterKey.Builder(context, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    class EncryptionInitializationException(cause: Throwable) : Exception(cause)

    companion object {
        private const val MASTER_KEY_ALIAS = "_awaladroid_master_key_"
    }
}
