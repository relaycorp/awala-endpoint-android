package tech.relaycorp.awaladroid.messaging

import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.relaycorp.awaladroid.GatewayProtocolException
import tech.relaycorp.awaladroid.test.MessageFactory
import tech.relaycorp.relaynet.bindings.pdc.ClientBindingException
import tech.relaycorp.relaynet.bindings.pdc.RejectedParcelException
import tech.relaycorp.relaynet.bindings.pdc.ServerConnectionException
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import tech.relaycorp.relaynet.testing.pdc.DeliverParcelCall
import tech.relaycorp.relaynet.testing.pdc.MockPDCClient

internal class SendMessageTest {

    private lateinit var pdcClient: MockPDCClient
    private val coroutineScope = TestCoroutineScope()
    private val subject = SendMessage({ pdcClient }, coroutineScope.coroutineContext)

    @Test
    fun deliverParcelToPublicEndpoint() = coroutineScope.runBlockingTest {
        val deliverParcelCall = DeliverParcelCall()
        pdcClient = MockPDCClient(deliverParcelCall)
        val message = MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC)

        subject.send(message)

        assertTrue(deliverParcelCall.wasCalled)
        val parcel = Parcel.deserialize(deliverParcelCall.arguments!!.parcelSerialized)
        assertEquals(message.parcel.id, parcel.id)
    }

    @Test
    fun deliverParcelSigner() = coroutineScope.runBlockingTest {
        val deliverParcelCall = DeliverParcelCall()
        pdcClient = MockPDCClient(deliverParcelCall)
        val message = MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC)

        subject.send(message)

        assertTrue(deliverParcelCall.wasCalled)
        val signer = deliverParcelCall.arguments!!.deliverySigner
        assertEquals(
            message.senderEndpoint.identityCertificate.subjectPrivateAddress,
            signer.certificate.subjectPrivateAddress
        )
    }

    @Test(expected = SendMessageException::class)
    fun deliverParcelWithServerError() = coroutineScope.runBlockingTest {
        val deliverParcelCall = DeliverParcelCall(ServerConnectionException(""))
        pdcClient = MockPDCClient(deliverParcelCall)

        subject.send(MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC))
    }

    @Test(expected = GatewayProtocolException::class)
    fun deliverParcelWithClientError() = coroutineScope.runBlockingTest {
        val deliverParcelCall = DeliverParcelCall(ClientBindingException(""))
        pdcClient = MockPDCClient(deliverParcelCall)

        subject.send(MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC))
    }

    @Test(expected = RejectedMessageException::class)
    fun deliverParcelWithRejectedParcelError() = coroutineScope.runBlockingTest {
        val deliverParcelCall = DeliverParcelCall(RejectedParcelException(""))
        pdcClient = MockPDCClient(deliverParcelCall)

        subject.send(MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC))
    }
}
