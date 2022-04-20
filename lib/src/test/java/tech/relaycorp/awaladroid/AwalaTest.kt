package tech.relaycorp.awaladroid

import android.content.Context
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import java.io.File
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.pki.CertificationPath
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.wrappers.privateAddress

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

        assertEquals(Dispatchers.IO, context.channelManager.coroutineContext)
        // Cause shared preferences to be resolved before inspecting it
        context.channelManager.sharedPreferences
        verify(androidContextSpy).getSharedPreferences("awaladroid-channels", Context.MODE_PRIVATE)
    }

    @Test
    public fun deleteExpiredOnSetUp(): Unit = runBlockingTest {
        val androidContext = RuntimeEnvironment.getApplication()
        Awala.setUp(androidContext)
        val originalAwalaContext = Awala.getContextOrThrow()
        val interval = Duration.ofSeconds(3)
        val expiringCertificate = issueEndpointCertificate(
            subjectPublicKey = KeyPairSet.PRIVATE_ENDPOINT.public,
            issuerPrivateKey = KeyPairSet.PRIVATE_GW.private,
            validityEndDate = ZonedDateTime.now().plus(interval),
        )

        val certificateStore = originalAwalaContext.certificateStore
        certificateStore.save(
            CertificationPath(expiringCertificate, emptyList()),
            expiringCertificate.issuerCommonName,
        )
        assertNotNull(
            certificateStore.retrieveLatest(
                expiringCertificate.subjectPrivateAddress,
                expiringCertificate.issuerCommonName,
            )
        )

        // Retry until expiration
        repeat(3) {
            runCatching { Thread.sleep(interval.toMillis()) }
            Awala.setUp(androidContext)
            advanceUntilIdle()
            certificateStore.retrieveLatest(
                KeyPairSet.PRIVATE_ENDPOINT.public.privateAddress,
                KeyPairSet.PRIVATE_GW.private.privateAddress
            ) ?: return@runBlockingTest
        }
        throw AssertionError("Expired certificate not deleted")
    }
}
