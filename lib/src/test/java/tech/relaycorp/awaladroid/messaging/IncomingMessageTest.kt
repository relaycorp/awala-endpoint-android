package tech.relaycorp.awaladroid.messaging

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import tech.relaycorp.awaladroid.endpoint.UnknownFirstPartyEndpointException
import tech.relaycorp.awaladroid.endpoint.UnknownThirdPartyEndpointException
import tech.relaycorp.awaladroid.test.EndpointChannel
import tech.relaycorp.awaladroid.test.MockContextTestCase
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.messages.payloads.ServiceMessage
import tech.relaycorp.relaynet.nodes.EndpointManager
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import tech.relaycorp.relaynet.testing.keystores.MockPrivateKeyStore
import tech.relaycorp.relaynet.testing.keystores.MockSessionPublicKeyStore
import tech.relaycorp.relaynet.testing.pki.PDACertPath

internal class IncomingMessageTest : MockContextTestCase() {
    private val serviceMessage = ServiceMessage("the type", "the content".toByteArray())

    @Test
    fun build_valid() = runBlockingTest {
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)
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

    @Test
    fun build_unknownRecipient() = runBlockingTest {
        val parcel = Parcel(
            "0deadbeef", // Non-existing first-party endpoint
            "payload".toByteArray(),
            PDACertPath.PDA,
        )

        val exception = assertThrows(UnknownFirstPartyEndpointException::class.java) {
            runBlocking {
                IncomingMessage.build(parcel) {}
            }
        }

        assertEquals("Unknown first-party endpoint ${parcel.recipientAddress}", exception.message)
    }

    @Test
    fun build_unknownSender() = runBlockingTest {
        val firstPartyEndpoint = createFirstPartyEndpoint()
        val parcel = Parcel(
            firstPartyEndpoint.privateAddress,
            "payload".toByteArray(),
            PDACertPath.PDA,
        )

        val exception = assertThrows(UnknownThirdPartyEndpointException::class.java) {
            runBlocking {
                IncomingMessage.build(parcel) {}
            }
        }

        assertEquals(
            "Unknown third-party endpoint ${PDACertPath.PDA.subjectPrivateAddress} for " +
                "first-party endpoint ${firstPartyEndpoint.privateAddress}",
            exception.message,
        )
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
