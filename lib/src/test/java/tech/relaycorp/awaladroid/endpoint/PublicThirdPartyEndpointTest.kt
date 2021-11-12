package tech.relaycorp.awaladroid.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.util.UUID
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import tech.relaycorp.awaladroid.test.FirstPartyEndpointFactory
import tech.relaycorp.awaladroid.test.MockContextTestCase
import tech.relaycorp.awaladroid.test.ThirdPartyEndpointFactory
import tech.relaycorp.relaynet.PublicNodeConnectionParams
import tech.relaycorp.relaynet.SessionKeyPair
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import tech.relaycorp.relaynet.wrappers.privateAddress

internal class PublicThirdPartyEndpointTest : MockContextTestCase() {
    private val publicAddress = "example.org"

    @Test
    fun privateAddress() {
        val identityKey = KeyPairSet.PDA_GRANTEE.public
        val thirdPartyEndpoint = PublicThirdPartyEndpoint(
            publicAddress,
            identityKey,
        )

        assertEquals(identityKey.privateAddress, thirdPartyEndpoint.privateAddress)
    }

    @Test
    fun address() {
        val thirdPartyEndpoint = PublicThirdPartyEndpoint(
            publicAddress,
            KeyPairSet.PDA_GRANTEE.public,
        )

        assertEquals("https://$publicAddress", thirdPartyEndpoint.address)
    }

    @Test
    fun load_successful() = runBlockingTest {
        val privateAddress = UUID.randomUUID().toString()
        whenever(storage.publicThirdParty.get(any()))
            .thenReturn(PublicThirdPartyEndpointData(publicAddress, KeyPairSet.PDA_GRANTEE.public))

        val endpoint = PublicThirdPartyEndpoint.load(privateAddress)!!
        assertEquals(publicAddress, endpoint.publicAddress)
        assertEquals(KeyPairSet.PDA_GRANTEE.public, endpoint.identityKey)
    }

    @Test
    fun load_nonExistent() = runBlockingTest {
        whenever(storage.publicThirdParty.get(any())).thenReturn(null)

        assertNull(PublicThirdPartyEndpoint.load(UUID.randomUUID().toString()))
    }

    @Test
    fun import_validConnectionParams() = runBlockingTest {
        val connectionParams = PublicNodeConnectionParams(
            publicAddress,
            KeyPairSet.PDA_GRANTEE.public,
            SessionKeyPair.generate().sessionKey
        )

        val thirdPartyEndpoint = PublicThirdPartyEndpoint.import(connectionParams.serialize())

        assertEquals(connectionParams.publicAddress, thirdPartyEndpoint.publicAddress)
        assertEquals(connectionParams.identityKey, thirdPartyEndpoint.identityKey)
        verify(storage.publicThirdParty).set(
            PDACertPath.PDA.subjectPrivateAddress,
            PublicThirdPartyEndpointData(
                connectionParams.publicAddress,
                connectionParams.identityKey
            )
        )
        sessionPublicKeystore.retrieve(thirdPartyEndpoint.privateAddress)
    }

    @Test
    fun import_invalidConnectionParams() = runBlockingTest {
        val exception = assertThrows(InvalidThirdPartyEndpoint::class.java) {
            runBlockingTest {
                PublicThirdPartyEndpoint.import(
                    "malformed".toByteArray()
                )
            }
        }

        assertEquals("Connection params serialization is malformed", exception.message)
        assertEquals(0, sessionPublicKeystore.keys.size)
    }

    @Test
    fun dataSerialization() {
        val identityKey = KeyPairSet.PDA_GRANTEE.public

        val dataSerialized = PublicThirdPartyEndpointData(publicAddress, identityKey).serialize()
        val data = PublicThirdPartyEndpointData.deserialize(dataSerialized)

        assertEquals(publicAddress, data.publicAddress)
        assertEquals(identityKey, data.identityKey)
    }

    @Test
    fun delete() = runBlockingTest {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        val thirdPartyEndpoint = ThirdPartyEndpointFactory.buildPublic()
        val ownSessionKeyPair = SessionKeyPair.generate()
        privateKeyStore.saveSessionKey(
            ownSessionKeyPair.privateKey,
            ownSessionKeyPair.sessionKey.keyId,
            firstPartyEndpoint.privateAddress,
            thirdPartyEndpoint.privateAddress
        )
        val peerSessionKey = SessionKeyPair.generate().sessionKey
        sessionPublicKeystore.save(peerSessionKey, thirdPartyEndpoint.privateAddress)

        thirdPartyEndpoint.delete()

        verify(storage.publicThirdParty).delete(thirdPartyEndpoint.privateAddress)
        assertEquals(0, privateKeyStore.sessionKeys[firstPartyEndpoint.privateAddress]!!.size)
        assertEquals(0, sessionPublicKeystore.keys.size)
    }
}
