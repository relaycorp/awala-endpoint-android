package tech.relaycorp.awaladroid.messaging

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import nl.altindag.log.LogCaptor
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.relaycorp.awaladroid.endpoint.PublicThirdPartyEndpoint
import tech.relaycorp.awaladroid.endpoint.UnknownFirstPartyEndpointException
import tech.relaycorp.awaladroid.endpoint.UnknownThirdPartyEndpointException
import tech.relaycorp.awaladroid.test.EndpointChannel
import tech.relaycorp.awaladroid.test.MockContextTestCase
import tech.relaycorp.relaynet.issueDeliveryAuthorization
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.messages.payloads.ServiceMessage
import tech.relaycorp.relaynet.nodes.EndpointManager
import tech.relaycorp.relaynet.pki.CertificationPath
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import tech.relaycorp.relaynet.testing.keystores.MockPrivateKeyStore
import tech.relaycorp.relaynet.testing.keystores.MockSessionPublicKeyStore
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath

internal class IncomingMessageTest : MockContextTestCase() {
    private val thirdPartyEndpointCertificate = issueEndpointCertificate(
        KeyPairSet.PDA_GRANTEE.public,
        KeyPairSet.PRIVATE_GW.private,
        ZonedDateTime.now().plusDays(1),
        PDACertPath.PRIVATE_GW,
    )

    @After
    fun clearLogs() = logCaptor.clearLogs()

    @Test
    fun build_valid() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)
        val thirdPartyEndpointManager = makeThirdPartyEndpointManager(channel)
        val serviceMessage = ServiceMessage("the type", "the content".toByteArray())
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

        assertEquals(PDACertPath.PRIVATE_ENDPOINT, message!!.recipientEndpoint.identityCertificate)
        assertEquals(serviceMessage.type, message.type)
        assertArrayEquals(serviceMessage.content, message.content)
    }

    @Test
    fun build_unknownRecipient() = runTest {
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
    fun build_unknownSender() = runTest {
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

    @Test
    fun build_pdaPath_fromPublicEndpoint() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)
        val parcel = Parcel(
            channel.firstPartyEndpoint.privateAddress,
            makePDAPathParcelPayload(channel, "doesn't matter".toByteArray()),
            PDACertPath.PDA,
        )
        val ack = StubACK()

        val message = IncomingMessage.build(parcel, ack::run)

        assertNull(message)
        assertTrue(ack.wasCalled)
        val thirdPartyEndpoint = channel.thirdPartyEndpoint as PublicThirdPartyEndpoint
        assertTrue(
            logCaptor.infoLogs.contains(
                "Ignoring PDA path from public endpoint ${thirdPartyEndpoint.privateAddress} " +
                    "(${thirdPartyEndpoint.publicAddress})"
            )
        )
    }

    @Test
    fun build_pdaPath_malformed() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PRIVATE)
        val parcel = Parcel(
            channel.firstPartyEndpoint.privateAddress,
            makePDAPathParcelPayload(channel, "malformed".toByteArray()),
            PDACertPath.PDA,
        )
        val ack = StubACK()

        val message = IncomingMessage.build(parcel, ack::run)

        assertNull(message)
        assertTrue(ack.wasCalled)
        verify(storage.privateThirdParty, never()).set(any(), any())
        assertTrue(
            logCaptor.infoLogs.contains(
                "Ignoring malformed PDA path for ${channel.firstPartyEndpoint.privateAddress} " +
                    "from ${channel.thirdPartyEndpoint.privateAddress}"
            )
        )
    }

    @Test
    fun build_pdaPath_invalid() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PRIVATE)
        val now = ZonedDateTime.now()
        val expiredPDA = issueDeliveryAuthorization(
            channel.firstPartyEndpoint.publicKey,
            KeyPairSet.PDA_GRANTEE.private,
            now.minusSeconds(1),
            thirdPartyEndpointCertificate,
            now.minusSeconds(2),
        )
        val parcel = Parcel(
            channel.firstPartyEndpoint.privateAddress,
            makePDAPathParcelPayload(
                channel,
                CertificationPath(expiredPDA, listOf(thirdPartyEndpointCertificate))
            ),
            PDACertPath.PDA,
        )
        val ack = StubACK()

        val message = IncomingMessage.build(parcel, ack::run)

        assertNull(message)
        assertTrue(ack.wasCalled)
        verify(storage.privateThirdParty, never()).set(any(), any())
        assertTrue(
            logCaptor.infoLogs.contains(
                "Ignoring invalid PDA path for ${channel.firstPartyEndpoint.privateAddress} " +
                    "from ${channel.thirdPartyEndpoint.privateAddress}"
            )
        )
    }

    @Test
    fun build_pdaPath_valid() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PRIVATE)
        val pda = issueDeliveryAuthorization(
            channel.firstPartyEndpoint.publicKey,
            KeyPairSet.PDA_GRANTEE.private,
            thirdPartyEndpointCertificate.expiryDate,
            thirdPartyEndpointCertificate,
        )
        val pdaPath = CertificationPath(pda, listOf(thirdPartyEndpointCertificate))
        val parcel = Parcel(
            channel.firstPartyEndpoint.privateAddress,
            makePDAPathParcelPayload(channel, pdaPath),
            PDACertPath.PDA,
        )
        val ack = StubACK()

        val message = IncomingMessage.build(parcel, ack::run)

        val thirdPartyEndpoint = channel.thirdPartyEndpoint
        assertNull(message)
        assertTrue(ack.wasCalled)
        assertTrue(
            logCaptor.infoLogs.contains(
                "Updated PDA path from ${thirdPartyEndpoint.privateAddress} for " +
                    channel.firstPartyEndpoint.privateAddress
            )
        )
        verify(storage.privateThirdParty).set(
            eq("${channel.firstPartyEndpoint.privateAddress}_${thirdPartyEndpoint.privateAddress}"),
            argThat {
                identityKey == thirdPartyEndpoint.identityKey &&
                    this.pdaPath.leafCertificate == pda &&
                    this.pdaPath.certificateAuthorities == pdaPath.certificateAuthorities
            },
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

    private suspend fun makePDAPathParcelPayload(
        channel: EndpointChannel,
        pdaPath: CertificationPath
    ): ByteArray = makePDAPathParcelPayload(channel, pdaPath.serialize())

    private suspend fun makePDAPathParcelPayload(
        channel: EndpointChannel,
        pdaPathSerialized: ByteArray
    ): ByteArray {
        val thirdPartyEndpointManager = makeThirdPartyEndpointManager(channel)
        val pdaPathServiceMessage = makePDAPathMessage(pdaPathSerialized)
        return thirdPartyEndpointManager.wrapMessagePayload(
            pdaPathServiceMessage,
            channel.firstPartyEndpoint.privateAddress,
            channel.thirdPartyEndpoint.privateAddress
        )
    }

    private fun makePDAPathMessage(content: ByteArray) =
        ServiceMessage("application/vnd+relaycorp.awala.pda-path", content)

    companion object {
        private val logCaptor = LogCaptor.forClass(IncomingMessage::class.java)

        @AfterClass
        fun closeLogs() = logCaptor.close()
    }
}
