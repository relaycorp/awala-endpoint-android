package tech.relaycorp.awaladroid

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import tech.relaycorp.awala.keystores.file.FileKeystoreRoot
import tech.relaycorp.awaladroid.test.FakeAndroidKeyStore
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath

@RunWith(RobolectricTestRunner::class)
public class AndroidPrivateKeyStoreTest {

    @Before
    public fun setUp() {
        FakeAndroidKeyStore.setup
    }

    @Test
    public fun saveAndRetrieve(): Unit = runTest {
        val androidContext = RuntimeEnvironment.getApplication()
        val root = FileKeystoreRoot(File(androidContext.filesDir, "tmp-keystore"))
        val store = AndroidPrivateKeyStore(root, androidContext)
        val id = KeyPairSet.PRIVATE_ENDPOINT.private
        val certificate = PDACertPath.PRIVATE_ENDPOINT

        store.saveIdentityKey(id)
        val retrievedId = store.retrieveIdentityKey(certificate.subjectId)
        assertEquals(id, retrievedId)
    }
}
