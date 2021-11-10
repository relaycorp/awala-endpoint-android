package tech.relaycorp.awaladroid.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.util.UUID
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import tech.relaycorp.awaladroid.Awala
import tech.relaycorp.awaladroid.storage.StorageImpl
import tech.relaycorp.awaladroid.storage.mockStorage
import tech.relaycorp.awaladroid.test.ThirdPartyEndpointFactory
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath

internal class PublicThirdPartyEndpointTest {

    private lateinit var storage: StorageImpl

    @Rule
    @JvmField
    val expectedException: ExpectedException = ExpectedException.none()

    @Before
    fun setUp() {
        storage = mockStorage().also { Awala.storage = it }
    }

    @Test
    fun load_successful() = runBlockingTest {
        val privateAddress = UUID.randomUUID().toString()
        val publicAddress = "example.org"
        whenever(storage.publicThirdParty.get(any()))
            .thenReturn(PublicThirdPartyEndpointData(publicAddress, KeyPairSet.PDA_GRANTEE.public))

        val endpoint = PublicThirdPartyEndpoint.load(privateAddress)!!
        assertEquals(publicAddress, endpoint.publicAddress)
        assertEquals("https://$publicAddress", endpoint.address)
        assertEquals(KeyPairSet.PDA_GRANTEE.public, endpoint.identityKey)
    }

    @Test
    fun load_nonExistent() = runBlockingTest {
        whenever(storage.publicThirdParty.get(any())).thenReturn(null)

        assertNull(PublicThirdPartyEndpoint.load(UUID.randomUUID().toString()))
    }

    @Test
    fun import_successful() = runBlockingTest {
        val publicAddress = "example.org"
        val identityKey = KeyPairSet.PDA_GRANTEE.public
        with(PublicThirdPartyEndpoint.import(publicAddress, identityKey.encoded)) {
            assertEquals(publicAddress, this.publicAddress)
            assertEquals(identityKey, identityKey)
            assertEquals("https://$publicAddress", this.address)
        }

        verify(storage.publicThirdParty).set(
            PDACertPath.PDA.subjectPrivateAddress,
            PublicThirdPartyEndpointData(
                publicAddress,
                identityKey
            )
        )
    }

    @Test
    fun import_malformedCertificate() = runBlockingTest {
        expectedException.expect(InvalidThirdPartyEndpoint::class.java)
        expectedException.expectMessage("Identity key is not a well-formed RSA public key")

        PublicThirdPartyEndpoint.import("example.org", "malformed".toByteArray())
    }

    @Test
    fun dataSerialization() {
        val publicAddress = "example.org"
        val identityKey = KeyPairSet.PDA_GRANTEE.public

        val dataSerialized = PublicThirdPartyEndpointData(publicAddress, identityKey).serialize()
        val data = PublicThirdPartyEndpointData.deserialize(dataSerialized)

        assertEquals(publicAddress, data.publicAddress)
        assertEquals(identityKey, data.identityKey)
    }

    @Test
    fun delete() = runBlockingTest {
        val endpoint = ThirdPartyEndpointFactory.buildPublic()
        endpoint.delete()
        verify(storage.publicThirdParty).delete(endpoint.privateAddress)
    }
}
