package tech.relaycorp.awaladroid

import java.io.File
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import tech.relaycorp.awala.keystores.file.FileKeystoreRoot
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath

@RunWith(RobolectricTestRunner::class)
public class AndroidPrivateKeyStoreTest {
    @Test
    public fun saveAndRetrieve(): Unit = runBlockingTest {
        val androidContext = RuntimeEnvironment.getApplication()
        val root = FileKeystoreRoot(File(androidContext.filesDir, "tmp-keystore"))
        val store = AndroidPrivateKeyStore(root, androidContext)
        val privateKey = KeyPairSet.PRIVATE_ENDPOINT.private
        val certificate = PDACertPath.PRIVATE_ENDPOINT

        store.saveIdentityKey(privateKey)
        val retrievedPrivateKey = store.retrieveIdentityKey(certificate.subjectPrivateAddress)
        assertEquals(privateKey, retrievedPrivateKey)
    }
}
