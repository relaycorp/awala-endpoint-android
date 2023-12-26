package tech.relaycorp.awaladroid.messaging

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
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
import tech.relaycorp.awaladroid.test.RecipientAddressType
import tech.relaycorp.relaynet.PrivateEndpointConnParams
import tech.relaycorp.relaynet.SessionKeyPair
import tech.relaycorp.relaynet.issueDeliveryAuthorization
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.messages.Recipient
import tech.relaycorp.relaynet.messages.payloads.ServiceMessage
import tech.relaycorp.relaynet.nodes.EndpointManager
import tech.relaycorp.relaynet.pki.CertificationPath
import tech.relaycorp.relaynet.testing.keystores.MockPrivateKeyStore
import tech.relaycorp.relaynet.testing.keystores.MockSessionPublicKeyStore
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import java.time.ZonedDateTime

internal class IncomingMessageTest : MockContextTestCase() {
    private val thirdPartyEndpointCertificate =
        issueEndpointCertificate(
            KeyPairSet.PDA_GRANTEE.public,
            KeyPairSet.PRIVATE_GW.private,
            ZonedDateTime.now().plusDays(1),
            PDACertPath.PRIVATE_GW,
        )

    @After
    fun clearLogs() = logCaptor.clearLogs()

    @Test
    fun build_valid() =
        runTest {
            val channel = createEndpointChannel(RecipientAddressType.PUBLIC)
            val thirdPartyEndpointManager = makeThirdPartyEndpointManager(channel)
            val serviceMessage = ServiceMessage("the type", "the content".toByteArray())
            val parcel =
                Parcel(
                    recipient =
                        Recipient(
                            channel.firstPartyEndpoint.nodeId,
                            channel.firstPartyEndpoint.nodeId,
                        ),
                    payload =
                        thirdPartyEndpointManager.wrapMessagePayload(
                            serviceMessage,
                            channel.firstPartyEndpoint.nodeId,
                            channel.thirdPartyEndpoint.nodeId,
                        ),
                    senderCertificate = PDACertPath.PDA,
                )

            val message = IncomingMessage.build(parcel) {}

            assertEquals(
                PDACertPath.PRIVATE_ENDPOINT,
                message!!.recipientEndpoint.identityCertificate,
            )
            assertEquals(serviceMessage.type, message.type)
            assertArrayEquals(serviceMessage.content, message.content)
        }

    @Test
    fun build_unknownRecipient() =
        runTest {
            val parcel =
                Parcel(
                    // Non-existing first-party endpoint
                    Recipient("0deadbeef"),
                    "payload".toByteArray(),
                    PDACertPath.PDA,
                )

            val exception =
                assertThrows(UnknownFirstPartyEndpointException::class.java) {
                    runBlocking {
                        IncomingMessage.build(parcel) {}
                    }
                }

            assertEquals("Unknown first-party endpoint ${parcel.recipient.id}", exception.message)
        }

    @Test
    fun build_unknownSender() =
        runTest {
            val firstPartyEndpoint = createFirstPartyEndpoint()
            val parcel =
                Parcel(
                    Recipient(firstPartyEndpoint.nodeId, firstPartyEndpoint.nodeId),
                    "payload".toByteArray(),
                    PDACertPath.PDA,
                )

            val exception =
                assertThrows(UnknownThirdPartyEndpointException::class.java) {
                    runBlocking {
                        IncomingMessage.build(parcel) {}
                    }
                }

            assertEquals(
                "Unknown third-party endpoint ${PDACertPath.PDA.subjectId} for " +
                    "first-party endpoint ${firstPartyEndpoint.nodeId}",
                exception.message,
            )
        }

    @Test
    fun build_pdaPath_fromPublicEndpoint() =
        runTest {
            val channel = createEndpointChannel(RecipientAddressType.PUBLIC)
            val parcel =
                Parcel(
                    Recipient(channel.firstPartyEndpoint.nodeId, channel.firstPartyEndpoint.nodeId),
                    encryptParcelPayload(channel, "doesn't matter".toByteArray()),
                    PDACertPath.PDA,
                )
            val ack = StubACK()

            val message = IncomingMessage.build(parcel, ack::run)

            assertNull(message)
            assertTrue(ack.wasCalled)
            val thirdPartyEndpoint = channel.thirdPartyEndpoint as PublicThirdPartyEndpoint
            assertTrue(
                logCaptor.infoLogs.contains(
                    "Ignoring connection params from public endpoint " +
                        "${thirdPartyEndpoint.nodeId} (${thirdPartyEndpoint.internetAddress})",
                ),
            )
        }

    @Test
    fun build_connParams_malformed() =
        runTest {
            val channel = createEndpointChannel(RecipientAddressType.PRIVATE)
            val parcel =
                Parcel(
                    Recipient(channel.firstPartyEndpoint.nodeId, channel.firstPartyEndpoint.nodeId),
                    encryptParcelPayload(channel, "malformed".toByteArray()),
                    PDACertPath.PDA,
                )
            val ack = StubACK()

            val message = IncomingMessage.build(parcel, ack::run)

            assertNull(message)
            assertTrue(ack.wasCalled)
            verify(storage.privateThirdParty, never()).set(any(), any())
            assertTrue(
                logCaptor.infoLogs.contains(
                    "Ignoring malformed connection params " +
                        "for ${channel.firstPartyEndpoint.nodeId} " +
                        "from ${channel.thirdPartyEndpoint.nodeId}",
                ),
            )
        }

    @Test
    fun build_connParams_invalid() =
        runTest {
            val channel = createEndpointChannel(RecipientAddressType.PRIVATE)
            val now = ZonedDateTime.now()
            val expiredPDA =
                issueDeliveryAuthorization(
                    channel.firstPartyEndpoint.publicKey,
                    KeyPairSet.PDA_GRANTEE.private,
                    now.minusSeconds(1),
                    thirdPartyEndpointCertificate,
                    now.minusSeconds(2),
                )
            val deliveryAuth = CertificationPath(expiredPDA, listOf(thirdPartyEndpointCertificate))
            val params = makeConnParams(channel, deliveryAuth)
            val parcel =
                Parcel(
                    Recipient(channel.firstPartyEndpoint.nodeId, channel.firstPartyEndpoint.nodeId),
                    encryptConnectionParams(channel, params),
                    PDACertPath.PDA,
                )
            val ack = StubACK()

            val message = IncomingMessage.build(parcel, ack::run)

            assertNull(message)
            assertTrue(ack.wasCalled)
            verify(storage.privateThirdParty, never()).set(any(), any())
            assertTrue(
                logCaptor.infoLogs.contains(
                    "Ignoring invalid connection params for ${channel.firstPartyEndpoint.nodeId} " +
                        "from ${channel.thirdPartyEndpoint.nodeId}",
                ),
            )
        }

    @Test
    fun build_connParams_valid() =
        runTest {
            val channel = createEndpointChannel(RecipientAddressType.PRIVATE)
            val pda =
                issueDeliveryAuthorization(
                    channel.firstPartyEndpoint.publicKey,
                    KeyPairSet.PDA_GRANTEE.private,
                    thirdPartyEndpointCertificate.expiryDate,
                    thirdPartyEndpointCertificate,
                )
            val delivAuth = CertificationPath(pda, listOf(thirdPartyEndpointCertificate))
            val connectionParams = makeConnParams(channel, delivAuth)
            val parcel =
                Parcel(
                    Recipient(channel.firstPartyEndpoint.nodeId),
                    encryptConnectionParams(channel, connectionParams),
                    PDACertPath.PDA,
                )
            val ack = StubACK()

            val message = IncomingMessage.build(parcel, ack::run)

            val thirdPartyEndpoint = channel.thirdPartyEndpoint
            assertNull(message)
            assertTrue(ack.wasCalled)
            assertTrue(
                logCaptor.infoLogs.contains(
                    "Updated connection params from ${thirdPartyEndpoint.nodeId} for " +
                        channel.firstPartyEndpoint.nodeId,
                ),
            )
            verify(storage.privateThirdParty).set(
                eq("${channel.firstPartyEndpoint.nodeId}_${thirdPartyEndpoint.nodeId}"),
                argThat {
                    identityKey == thirdPartyEndpoint.identityKey &&
                        this.pdaPath.leafCertificate == pda &&
                        this.pdaPath.certificateAuthorities == delivAuth.certificateAuthorities &&
                        this.internetGatewayAddress == thirdPartyEndpoint.internetAddress
                },
            )
        }

    private fun makeConnParams(
        channel: EndpointChannel,
        deliveryAuth: CertificationPath,
    ) = PrivateEndpointConnParams(
        channel.thirdPartyEndpoint.identityKey,
        channel.thirdPartyEndpoint.internetAddress,
        deliveryAuth,
        SessionKeyPair.generate().sessionKey,
    )

    private suspend fun makeThirdPartyEndpointManager(channel: EndpointChannel): EndpointManager {
        val thirdPartyPrivateKeyStore = MockPrivateKeyStore()
        thirdPartyPrivateKeyStore.saveSessionKey(
            channel.thirdPartySessionKeyPair.privateKey,
            channel.thirdPartySessionKeyPair.sessionKey.keyId,
            channel.thirdPartyEndpoint.nodeId,
            channel.firstPartyEndpoint.nodeId,
        )
        val thirdPartySessionPublicKeyStore = MockSessionPublicKeyStore()
        thirdPartySessionPublicKeyStore.save(
            channel.firstPartySessionKeyPair.sessionKey,
            channel.firstPartyEndpoint.nodeId,
            channel.thirdPartyEndpoint.nodeId,
        )
        return EndpointManager(
            thirdPartyPrivateKeyStore,
            thirdPartySessionPublicKeyStore,
        )
    }

    private suspend fun encryptConnectionParams(
        channel: EndpointChannel,
        params: PrivateEndpointConnParams,
    ): ByteArray = encryptParcelPayload(channel, params.serialize())

    private suspend fun encryptParcelPayload(
        channel: EndpointChannel,
        plaintext: ByteArray,
    ): ByteArray {
        val thirdPartyEndpointManager = makeThirdPartyEndpointManager(channel)
        val pdaPathServiceMessage = makePDAPathMessage(plaintext)
        return thirdPartyEndpointManager.wrapMessagePayload(
            pdaPathServiceMessage,
            channel.firstPartyEndpoint.nodeId,
            channel.thirdPartyEndpoint.nodeId,
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
