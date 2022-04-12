package tech.relaycorp.awaladroid

import android.content.Context
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import tech.relaycorp.awala.keystores.file.FileCertificateStore
import tech.relaycorp.awala.keystores.file.FileSessionPublicKeystore
import tech.relaycorp.awaladroid.test.unsetAwalaContext

@RunWith(RobolectricTestRunner::class)
public class AwalaTest {
    @Before
    @After
    public fun tearDownAwala(): Unit = unsetAwalaContext()

    @Test
    public fun useBeforeSetup() {
        assertThrows(SetupPendingException::class.java) { Awala.getContextOrThrow() }
    }

    @Test
    public fun useAfterSetup(): Unit = runBlockingTest {
        Awala.setUp(RuntimeEnvironment.getApplication())

        Awala.getContextOrThrow()
    }

    @Test
    public fun keystores(): Unit = runBlockingTest {
        val androidContext = RuntimeEnvironment.getApplication()
        Awala.setUp(androidContext)

        val context = Awala.getContextOrThrow()

        assertTrue(context.privateKeyStore is AndroidPrivateKeyStore)
        assertTrue(context.sessionPublicKeyStore is FileSessionPublicKeystore)
        assertTrue(context.certificateStore is FileCertificateStore)
        val expectedRoot = File(androidContext.filesDir, "awaladroid${File.separator}keystores")
        assertEquals(
            expectedRoot,
            (context.privateKeyStore as AndroidPrivateKeyStore).rootDirectory.parentFile,
        )
        assertEquals(
            expectedRoot,
            (context.sessionPublicKeyStore as FileSessionPublicKeystore).rootDirectory.parentFile,
        )
        assertEquals(
            expectedRoot,
            (context.certificateStore as FileCertificateStore).rootDirectory.parentFile,
        )
    }

    @Test
    public fun channelManager(): Unit = runBlockingTest {
        val androidContextSpy = spy(RuntimeEnvironment.getApplication())
        Awala.setUp(androidContextSpy)

        val context = Awala.getContextOrThrow()

        verify(androidContextSpy).getSharedPreferences("awaladroid-channels", Context.MODE_PRIVATE)
        assertEquals(
            Dispatchers.IO,
            context.channelManager.flowSharedPreferences.coroutineContext,
        )
    }
}
