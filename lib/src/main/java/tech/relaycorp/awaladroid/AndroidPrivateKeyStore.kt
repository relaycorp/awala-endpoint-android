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
    private val encryptedFileBuilder: (File, MasterKey) -> EncryptedFile = { file, masterKey ->
        EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        )
            // Set a explicit preference name to avoid cryptic `AEADBadTagException`s when multiple
            // `MasterKey`s are used by the app.
            .setKeysetPrefName(ENCRYPTED_FILE_PREFERENCE_NAME)
            .build()
    },
) : FilePrivateKeyStore(root) {
    @Throws(EncryptionInitializationException::class)
    override fun makeEncryptedInputStream(file: File) = buildEncryptedFile(file).openFileInput()

    @Throws(EncryptionInitializationException::class)
    override fun makeEncryptedOutputStream(file: File) = buildEncryptedFile(file).openFileOutput()

    @Throws(EncryptionInitializationException::class)
    private fun buildEncryptedFile(file: File): EncryptedFile =
        try {
            encryptedFileBuilder(file, masterKey)
        } catch (exception: AEADBadTagException) {
            // Known issue: https://issuetracker.google.com/issues/164901843
            throw EncryptionInitializationException(
                "Could not build encrypted file due to internal issue",
                exception,
            )
        }

    private val masterKey by lazy {
        MasterKey.Builder(context, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    companion object {
        private const val MASTER_KEY_ALIAS = "_awaladroid_master_key_"
        private const val ENCRYPTED_FILE_PREFERENCE_NAME = "awala-private-key-store"
    }
}

public class EncryptionInitializationException(message: String, cause: Throwable) :
    AwaladroidException(message, cause)
