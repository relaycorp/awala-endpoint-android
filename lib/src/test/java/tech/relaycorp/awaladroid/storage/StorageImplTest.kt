package tech.relaycorp.awaladroid.storage

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.nio.charset.Charset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.relaycorp.awaladroid.endpoint.PrivateThirdPartyEndpointData
import tech.relaycorp.awaladroid.endpoint.PublicThirdPartyEndpointData
import tech.relaycorp.awaladroid.storage.persistence.Persistence
import tech.relaycorp.relaynet.pki.CertificationPath
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath

internal class StorageImplTest {

    private val persistence = mock<Persistence>()
    private val storage = StorageImpl(persistence)

    @Test
    fun gatewayId() = runTest {
        val charset = Charset.forName("ASCII")
        storage.gatewayId.testGet(
            PDACertPath.PRIVATE_GW.subjectId.toByteArray(charset),
            PDACertPath.PRIVATE_GW.subjectId
        )
        storage.gatewayId.testSet(
            PDACertPath.PRIVATE_GW.subjectId,
            PDACertPath.PRIVATE_GW.subjectId.toByteArray(charset),
        )
        storage.gatewayId.testDelete()
    }

    @Test
    fun privateThirdParty() = runTest {
        val data = PrivateThirdPartyEndpointData(
            KeyPairSet.PRIVATE_ENDPOINT.public,
            CertificationPath(
                PDACertPath.PDA,
                listOf(PDACertPath.PRIVATE_GW)
            )
        )
        val rawData = data.serialize()

        storage.privateThirdParty.testGet(rawData, data) { a, b ->
            a.identityKey == b.identityKey &&
                a.pdaPath.leafCertificate == b.pdaPath.leafCertificate &&
                a.pdaPath.certificateAuthorities == b.pdaPath.certificateAuthorities
        }
        storage.privateThirdParty.testSet(data, rawData)
        storage.privateThirdParty.testDelete()
        storage.privateThirdParty.testDeleteAll()
        storage.privateThirdParty.testList()
    }

    @Test
    fun publicThirdParty() = runTest {
        val data = PublicThirdPartyEndpointData(
            "example.org",
            KeyPairSet.INTERNET_GW.public
        )
        val rawData = data.serialize()

        storage.publicThirdParty.testGet(rawData, data)
        storage.publicThirdParty.testSet(data, rawData)
        storage.publicThirdParty.testDelete()
        storage.publicThirdParty.testDeleteAll()
        storage.publicThirdParty.testList()
    }

    // Helpers

    private suspend fun <T : Any> StorageImpl.Module<T>.testGet(
        rawData: ByteArray,
        expectedOutput: T,
        equalityCheck: ((T, T) -> Boolean) = Any::equals
    ) {
        val key = UUID.randomUUID().toString()
        whenever(persistence.get(any())).thenReturn(rawData)
        val output = get(key)!!
        verify(persistence).get(eq("$prefix$key"))
        assertTrue(
            "expected $expectedOutput, got $output",
            equalityCheck(expectedOutput, output)
        )
    }

    private suspend fun <T> StorageImpl.Module<T>.testSet(
        data: T,
        expectedRawData: ByteArray
    ) {
        val key = UUID.randomUUID().toString()
        set(key, data)
        verify(persistence).set(eq("$prefix$key"), eq(expectedRawData))
    }

    private suspend fun <T> StorageImpl.Module<T>.testDelete() {
        val key = UUID.randomUUID().toString()
        delete(key)
        verify(persistence).delete(eq("$prefix$key"))
    }

    private suspend fun <T> StorageImpl.Module<T>.testDeleteAll() {
        deleteAll()
        verify(persistence).deleteAll(eq(prefix))
    }

    private suspend fun <T> StorageImpl.Module<T>.testList() {
        val key = UUID.randomUUID().toString()
        val keyWithPrefix = prefix + key
        whenever(persistence.list(any())).thenReturn(listOf(keyWithPrefix))
        val result = list()
        verify(persistence).list(eq(prefix))
        assertArrayEquals(arrayOf(key), result.toTypedArray())
    }

    private suspend fun <T : Any> StorageImpl.SingleModule<T>.testGet(
        rawData: ByteArray,
        expectedOutput: T,
        equalityCheck: ((T, T) -> Boolean) = Any::equals
    ) {
        whenever(persistence.get(any())).thenReturn(rawData)
        val output = get()!!
        verify(persistence).get(eq("${prefix}base"))
        assertTrue(
            "expected $expectedOutput, got $output",
            equalityCheck(expectedOutput, output)
        )
    }

    private suspend fun <T> StorageImpl.SingleModule<T>.testSet(
        data: T,
        expectedRawData: ByteArray
    ) {
        set(data)
        verify(persistence).set(eq("${prefix}base"), eq(expectedRawData))
    }
}
