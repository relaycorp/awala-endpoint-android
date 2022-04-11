package tech.relaycorp.awaladroid.messaging

import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import tech.relaycorp.awaladroid.endpoint.PublicThirdPartyEndpointData
import tech.relaycorp.awaladroid.storage.StorageImpl
import tech.relaycorp.awaladroid.test.EndpointChannel
import tech.relaycorp.awaladroid.test.MockContextTestCase
import tech.relaycorp.awaladroid.test.MockPersistence
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.messages.payloads.ServiceMessage
import tech.relaycorp.relaynet.nodes.EndpointManager
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import tech.relaycorp.relaynet.testing.keystores.MockPrivateKeyStore
import tech.relaycorp.relaynet.testing.keystores.MockSessionPublicKeyStore
import tech.relaycorp.relaynet.testing.pki.PDACertPath

internal class IncomingMessageTest : MockContextTestCase() {
    private val persistence = MockPersistence()
    override val storage = StorageImpl(persistence)

    @Before
    fun resetPersistence() = persistence.reset()

    @Test
    fun buildFromParcel() = runBlockingTest {
        val serviceMessage = ServiceMessage("the type", "the content".toByteArray())
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)
        storage.publicThirdParty.set(
            channel.thirdPartyEndpoint.privateAddress,
            PublicThirdPartyEndpointData(
                channel.thirdPartyEndpoint.address,
                channel.thirdPartyEndpoint.identityKey,
            )
        )
        val thirdPartyEndpointManager = makeThirdPartyEndpointManager(channel)
        val parcel = Parcel(
            recipientAddress = channel.firstPartyEndpoint.privateAddress,
            payload = thirdPartyEndpointManager.wrapMessagePayload(
                serviceMessage,
                channel.firstPartyEndpoint.privateAddress,
                channel.thirdPartyEndpoint.privateAddress
            ),
            senderCertificate = PDACertPath.PDA
        )

        val message = IncomingMessage.build(parcel) {}

        assertEquals(PDACertPath.PRIVATE_ENDPOINT, message.recipientEndpoint.identityCertificate)
        assertEquals(serviceMessage.type, message.type)
        assertArrayEquals(serviceMessage.content, message.content)
    }

    private suspend fun makeThirdPartyEndpointManager(channel: EndpointChannel): EndpointManager {
        val thirdPartyPrivateKeyStore = MockPrivateKeyStore()
        thirdPartyPrivateKeyStore.saveSessionKey(
            channel.thirdPartySessionKeyPair.privateKey,
            channel.thirdPartySessionKeyPair.sessionKey.keyId,
            channel.thirdPartyEndpoint.privateAddress,
            channel.firstPartyEndpoint.privateAddress,
        )
        val thirdPartySessionPublicKeyStore = MockSessionPublicKeyStore()
        thirdPartySessionPublicKeyStore.save(
            channel.firstPartySessionKeyPair.sessionKey,
            channel.firstPartyEndpoint.privateAddress,
        )
        return EndpointManager(
            thirdPartyPrivateKeyStore,
            thirdPartySessionPublicKeyStore
        )
    }
}
