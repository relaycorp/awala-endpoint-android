package tech.relaycorp.awaladroid.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tech.relaycorp.awaladroid.test.FirstPartyEndpointFactory
import tech.relaycorp.awaladroid.test.MockContextTestCase
import tech.relaycorp.awaladroid.test.ThirdPartyEndpointFactory
import tech.relaycorp.relaynet.NodeConnectionParams
import tech.relaycorp.relaynet.SessionKeyPair
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import tech.relaycorp.relaynet.wrappers.nodeId
import java.util.UUID

internal class PublicThirdPartyEndpointTest : MockContextTestCase() {
    private val internetAddress = "example.org"

    @Test
    fun nodeId() {
        val identityKey = KeyPairSet.PDA_GRANTEE.public
        val thirdPartyEndpoint =
            PublicThirdPartyEndpoint(
                internetAddress,
                identityKey,
            )

        assertEquals(identityKey.nodeId, thirdPartyEndpoint.nodeId)
    }

    @Test
    fun recipient() {
        val thirdPartyEndpoint =
            PublicThirdPartyEndpoint(
                internetAddress,
                KeyPairSet.PDA_GRANTEE.public,
            )

        val recipient = thirdPartyEndpoint.recipient
        assertEquals(thirdPartyEndpoint.nodeId, recipient.id)
        assertEquals(internetAddress, recipient.internetAddress)
    }

    @Test
    fun load_successful() =
        runTest {
            val id = UUID.randomUUID().toString()
            whenever(storage.publicThirdParty.get(any()))
                .thenReturn(
                    PublicThirdPartyEndpointData(
                        internetAddress,
                        KeyPairSet.PDA_GRANTEE.public,
                    ),
                )

            val endpoint = PublicThirdPartyEndpoint.load(id)!!
            assertEquals(internetAddress, endpoint.internetAddress)
            assertEquals(KeyPairSet.PDA_GRANTEE.public, endpoint.identityKey)
        }

    @Test
    fun load_nonExistent() =
        runTest {
            whenever(storage.publicThirdParty.get(any())).thenReturn(null)

            assertNull(PublicThirdPartyEndpoint.load(UUID.randomUUID().toString()))
        }

    @Test
    fun import_validConnectionParams() =
        runTest {
            val connectionParams =
                NodeConnectionParams(
                    internetAddress,
                    KeyPairSet.PDA_GRANTEE.public,
                    SessionKeyPair.generate().sessionKey,
                )

            val firstPartyEndpoint = createFirstPartyEndpoint()

            val thirdPartyEndpoint =
                PublicThirdPartyEndpoint.import(
                    connectionParams.serialize(),
                    firstPartyEndpoint,
                )

            assertEquals(connectionParams.internetAddress, thirdPartyEndpoint.internetAddress)
            assertEquals(connectionParams.identityKey, thirdPartyEndpoint.identityKey)
            verify(storage.publicThirdParty).set(
                PDACertPath.PDA.subjectId,
                PublicThirdPartyEndpointData(
                    connectionParams.internetAddress,
                    connectionParams.identityKey,
                ),
            )
            sessionPublicKeystore.retrieve(firstPartyEndpoint.nodeId, thirdPartyEndpoint.nodeId)
        }

    @Test
    fun import_invalidConnectionParams() =
        runTest {
            val firstPartyEndpoint = createFirstPartyEndpoint()
            try {
                PublicThirdPartyEndpoint.import(
                    "malformed".toByteArray(),
                    firstPartyEndpoint,
                )
            } catch (exception: InvalidThirdPartyEndpoint) {
                assertEquals("Connection params serialization is malformed", exception.message)
                assertEquals(0, sessionPublicKeystore.keys.size)
                return@runTest
            }

            assert(false)
        }

    @Test
    fun dataSerialization() {
        val identityKey = KeyPairSet.PDA_GRANTEE.public

        val dataSerialized = PublicThirdPartyEndpointData(internetAddress, identityKey).serialize()
        val data = PublicThirdPartyEndpointData.deserialize(dataSerialized)

        assertEquals(internetAddress, data.internetAddress)
        assertEquals(identityKey, data.identityKey)
    }

    @Test
    fun delete() =
        runTest {
            val firstPartyEndpoint = FirstPartyEndpointFactory.build()
            val thirdPartyEndpoint = ThirdPartyEndpointFactory.buildPublic()
            val ownSessionKeyPair = SessionKeyPair.generate()
            privateKeyStore.saveSessionKey(
                ownSessionKeyPair.privateKey,
                ownSessionKeyPair.sessionKey.keyId,
                firstPartyEndpoint.nodeId,
                thirdPartyEndpoint.nodeId,
            )
            val peerSessionKey = SessionKeyPair.generate().sessionKey
            sessionPublicKeystore.save(
                peerSessionKey,
                firstPartyEndpoint.nodeId,
                thirdPartyEndpoint.nodeId,
            )

            thirdPartyEndpoint.delete(firstPartyEndpoint)

            verify(storage.publicThirdParty).delete(thirdPartyEndpoint.nodeId)
            assertEquals(0, privateKeyStore.sessionKeys[firstPartyEndpoint.nodeId]!!.size)
            assertEquals(0, sessionPublicKeystore.keys.size)
            verify(channelManager).delete(firstPartyEndpoint, thirdPartyEndpoint)
        }
}
