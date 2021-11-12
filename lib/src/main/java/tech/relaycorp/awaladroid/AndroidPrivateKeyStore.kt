package tech.relaycorp.awaladroid

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import tech.relaycorp.awala.keystores.file.FileKeystoreRoot
import tech.relaycorp.awala.keystores.file.FilePrivateKeyStore

internal class AndroidPrivateKeyStore(
    root: FileKeystoreRoot,
    private val context: Context
) : FilePrivateKeyStore(root) {
    override fun makeEncryptedInputStream(file: File) = file.inputStream()

    override fun makeEncryptedOutputStream(file: File) = file.outputStream()

    private fun buildEncryptedFile(file: File) =
        EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

    private val masterKey by lazy {
        MasterKey.Builder(context, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    companion object {
        private const val MASTER_KEY_ALIAS = "_awaladroid_master_key_"
    }
}
