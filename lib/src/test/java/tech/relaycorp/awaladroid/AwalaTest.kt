package tech.relaycorp.awaladroid

import android.content.Context
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import tech.relaycorp.relaynet.wrappers.nodeId
import java.io.File
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
public class AwalaTest {
    @Before
    @After
    public fun tearDownAwala(): Unit = unsetAwalaContext()

    @Test(expected = SetupPendingException::class)
    public fun useBeforeSetup() {
        Awala.getContextOrThrow()
    }

    @Test
    public fun useAfterSetup(): Unit = runTest {
        Awala.setUp(RuntimeEnvironment.getApplication())

        Awala.getContextOrThrow()
    }

    @Test(expected = SetupPendingException::class)
    public fun awaitWithoutSetup(): Unit = runTest {
        Awala.awaitContextOrThrow(100.milliseconds)
    }

    @Test(expected = SetupPendingException::class)
    public fun awaitWithLateSetup(): Unit = runTest {
        CoroutineScope(UnconfinedTestDispatcher()).launch {
            delay(200.milliseconds)
            Awala.setUp(RuntimeEnvironment.getApplication())
        }
        Awala.awaitContextOrThrow(100.milliseconds)
    }

    @Test(expected = SetupPendingException::class)
    public fun awaitAfterSetup(): Unit = runTest {
        CoroutineScope(UnconfinedTestDispatcher()).launch {
            delay(500.milliseconds)
            Awala.setUp(RuntimeEnvironment.getApplication())
        }
        Awala.awaitContextOrThrow(1000.milliseconds)
    }

    @Test
    public fun keystores(): Unit = runTest {
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
    public fun channelManager(): Unit = runTest {
        val androidContextSpy = spy(RuntimeEnvironment.getApplication())
        Awala.setUp(androidContextSpy)

        val context = Awala.getContextOrThrow()

        assertEquals(Dispatchers.IO, context.channelManager.coroutineContext)
        // Cause shared preferences to be resolved before inspecting it
        context.channelManager.sharedPreferences
        verify(androidContextSpy).getSharedPreferences("awaladroid-channels", Context.MODE_PRIVATE)
    }

    @Test
    public fun deleteExpiredOnSetUp(): Unit = runTest {
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

        advanceUntilIdle()
        assertNotNull(
            certificateStore.retrieveLatest(
                expiringCertificate.subjectId,
                expiringCertificate.issuerCommonName,
            ),
        )

        // Retry until expiration
        repeat(3) {
            runCatching { Thread.sleep(interval.toMillis()) }
            Awala.setUp(androidContext)
            advanceUntilIdle()
            certificateStore.retrieveLatest(
                KeyPairSet.PRIVATE_ENDPOINT.public.nodeId,
                KeyPairSet.PRIVATE_GW.private.nodeId,
            ) ?: return@runTest
        }
        throw AssertionError("Expired certificate not deleted")
    }
}
