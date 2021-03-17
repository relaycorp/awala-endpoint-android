package tech.relaycorp.awaladroid.storage

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.util.UUID
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.relaycorp.awaladroid.endpoint.AuthorizationBundle
import tech.relaycorp.awaladroid.endpoint.PrivateThirdPartyEndpointData
import tech.relaycorp.awaladroid.endpoint.PublicThirdPartyEndpointData
import tech.relaycorp.awaladroid.storage.persistence.Persistence
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath

internal class StorageImplTest {

    private val persistence = mock<Persistence>()
    private val storage = StorageImpl(persistence)

    @Test
    fun identityKeyPair() = runBlockingTest {
        storage.identityKeyPair.testGet(
            KeyPairSet.PRIVATE_GW.private.encoded,
            KeyPairSet.PRIVATE_GW
        ) { a, b -> a.private == b.private && a.public == b.public }
        storage.identityKeyPair.testSet(
            KeyPairSet.PRIVATE_GW,
            KeyPairSet.PRIVATE_GW.private.encoded
        )
        storage.identityKeyPair.testDelete()
        storage.identityKeyPair.testDeleteAll()
        storage.identityKeyPair.testList()
    }

    @Test
    fun identityCertificate() = runBlockingTest {
        storage.identityCertificate.testGet(
            PDACertPath.PRIVATE_ENDPOINT.serialize(),
            PDACertPath.PRIVATE_ENDPOINT
        )
        storage.identityCertificate.testSet(
            PDACertPath.PRIVATE_ENDPOINT,
            PDACertPath.PRIVATE_ENDPOINT.serialize()
        )
        storage.identityCertificate.testDelete()
        storage.identityCertificate.testDeleteAll()
        storage.identityCertificate.testList()
    }

    @Test
    fun gatewayCertificate() = runBlockingTest {
        storage.gatewayCertificate.testGet(
            PDACertPath.PRIVATE_ENDPOINT.serialize(),
            PDACertPath.PRIVATE_ENDPOINT
        )
        storage.gatewayCertificate.testSet(
            PDACertPath.PRIVATE_ENDPOINT,
            PDACertPath.PRIVATE_ENDPOINT.serialize()
        )
        storage.gatewayCertificate.testDelete()
    }

    @Test
    fun privateThirdParty() = runBlockingTest {
        val data = PrivateThirdPartyEndpointData(
            PDACertPath.PRIVATE_ENDPOINT,
            AuthorizationBundle(
                PDACertPath.PDA.serialize(),
                listOf(PDACertPath.PRIVATE_GW.serialize())
            )
        )
        val rawData = data.serialize()

        storage.privateThirdParty.testGet(rawData, data) { a, b ->
            a.identityCertificate.subjectPublicKey == b.identityCertificate.subjectPublicKey &&
                a.authBundle.pdaSerialized.contentEquals(b.authBundle.pdaSerialized) &&
                a.authBundle.pdaChainSerialized.mapIndexed { index, bytes ->
                    bytes.contentEquals(b.authBundle.pdaChainSerialized[index])
                }.all { it }
        }
        storage.privateThirdParty.testSet(data, rawData)
        storage.privateThirdParty.testDelete()
        storage.privateThirdParty.testDeleteAll()
        storage.privateThirdParty.testList()
    }

    @Test
    fun publicThirdParty() = runBlockingTest {
        val data = PublicThirdPartyEndpointData(
            "example.org",
            PDACertPath.PUBLIC_GW
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
