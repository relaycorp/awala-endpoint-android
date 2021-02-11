package tech.relaycorp.relaydroid.messaging

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.relaycorp.poweb.PoWebClient
import tech.relaycorp.relaydroid.PublicThirdPartyEndpoint
import tech.relaycorp.relaydroid.test.FirstPartyEndpointFactory
import tech.relaycorp.relaydroid.test.MessageFactory
import tech.relaycorp.relaynet.bindings.pdc.ServerConnectionException
import tech.relaycorp.relaynet.messages.Parcel
import java.time.Duration

internal class SendMessageTest {

    private val poWebClient = mock<PoWebClient>()
    private val coroutineScope = TestCoroutineScope()
    private val subject = SendMessage({ poWebClient }, coroutineScope.coroutineContext)

    @Test(expected = InvalidMessageException::class)
    fun invalidMessage() = coroutineScope.runBlockingTest {
        val message = OutgoingMessage(
            ByteArray(0),
            FirstPartyEndpointFactory.build(),
            PublicThirdPartyEndpoint("http://example.org")
        )

        subject.send(message)
    }

    @Test
    fun deliverParcelToPublicEndpoint() = coroutineScope.runBlockingTest {
        val message = MessageFactory.buildOutgoing()
        subject.send(message)

        verify(poWebClient).deliverParcel(check { parcelSerialized ->
            val parcel = Parcel.deserialize(parcelSerialized)
            assertEquals(message.receiverEndpoint.address, parcel.recipientAddress)
            assertArrayEquals(message.message, parcel.payload)
            parcel.senderCertificate.let { cert ->
                cert.validate()
                assertEquals(message.senderEndpoint.keyPair.public, cert.subjectPublicKey)
                assertTrue(Duration.between(message.creationDate, cert.startDate).seconds < 2)
                assertTrue(Duration.between(message.expirationDate, cert.expiryDate).seconds < 2)
            }
            assertEquals(message.id.value, parcel.id)
            assertTrue(Duration.between(message.creationDate, parcel.creationDate).seconds < 2)
            assertEquals(message.ttl, parcel.ttl)
            assertArrayEquals(
                arrayOf(message.senderEndpoint.gatewayCertificate),
                parcel.senderCertificateChain.toTypedArray()
            )
        }, any())
    }

    @Test
    fun deliverParcelSigner() = coroutineScope.runBlockingTest {
        val message = MessageFactory.buildOutgoing()
        subject.send(message)

        verify(poWebClient).deliverParcel(any(), check { signer ->
            assertEquals(
                message.senderEndpoint.certificate.subjectPrivateAddress,
                signer.certificate.subjectPrivateAddress
            )
        })
    }

    @Test(expected = SendMessageException::class)
    fun deliverParcelWithError() = coroutineScope.runBlockingTest {
        val message = MessageFactory.buildOutgoing()
        whenever(poWebClient.deliverParcel(any(), any())).thenThrow(ServerConnectionException(""))

        subject.send(message)
    }
}
