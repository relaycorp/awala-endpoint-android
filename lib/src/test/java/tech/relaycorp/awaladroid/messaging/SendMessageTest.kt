package tech.relaycorp.awaladroid.messaging

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.relaycorp.awaladroid.GatewayProtocolException
import tech.relaycorp.awaladroid.test.MessageFactory
import tech.relaycorp.awaladroid.test.MockContextTestCase
import tech.relaycorp.awaladroid.test.RecipientAddressType
import tech.relaycorp.relaynet.bindings.pdc.ClientBindingException
import tech.relaycorp.relaynet.bindings.pdc.RejectedParcelException
import tech.relaycorp.relaynet.bindings.pdc.ServerConnectionException
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.testing.pdc.DeliverParcelCall
import tech.relaycorp.relaynet.testing.pdc.MockPDCClient

internal class SendMessageTest : MockContextTestCase() {

    private lateinit var pdcClient: MockPDCClient
    private val coroutineScope = TestScope()
    private val subject = SendMessage({ pdcClient }, coroutineScope.coroutineContext)

    @Test
    fun deliverParcelToPublicEndpoint() = coroutineScope.runTest {
        val deliverParcelCall = DeliverParcelCall()
        pdcClient = MockPDCClient(deliverParcelCall)
        val message =
            MessageFactory.buildOutgoing(createEndpointChannel(RecipientAddressType.PUBLIC))

        subject.send(message)

        assertTrue(deliverParcelCall.wasCalled)
        val parcel = Parcel.deserialize(deliverParcelCall.arguments!!.parcelSerialized)
        assertEquals(message.parcel.id, parcel.id)
    }

    @Test
    fun deliverParcelSigner() = coroutineScope.runTest {
        val deliverParcelCall = DeliverParcelCall()
        pdcClient = MockPDCClient(deliverParcelCall)
        val message =
            MessageFactory.buildOutgoing(createEndpointChannel(RecipientAddressType.PUBLIC))

        subject.send(message)

        assertTrue(deliverParcelCall.wasCalled)
        val signer = deliverParcelCall.arguments!!.deliverySigner
        assertEquals(
            message.senderEndpoint.identityCertificate.subjectId,
            signer.certificate.subjectId,
        )
    }

    @Test(expected = SendMessageException::class)
    fun deliverParcelWithServerError() = coroutineScope.runTest {
        val deliverParcelCall = DeliverParcelCall(ServerConnectionException(""))
        pdcClient = MockPDCClient(deliverParcelCall)

        val message =
            MessageFactory.buildOutgoing(createEndpointChannel(RecipientAddressType.PUBLIC))
        subject.send(message)
    }

    @Test(expected = GatewayProtocolException::class)
    fun deliverParcelWithClientError() = coroutineScope.runTest {
        val deliverParcelCall = DeliverParcelCall(ClientBindingException(""))
        pdcClient = MockPDCClient(deliverParcelCall)

        val message =
            MessageFactory.buildOutgoing(createEndpointChannel(RecipientAddressType.PUBLIC))
        subject.send(message)
    }

    @Test(expected = RejectedMessageException::class)
    fun deliverParcelWithRejectedParcelError() = coroutineScope.runTest {
        val deliverParcelCall = DeliverParcelCall(RejectedParcelException(""))
        pdcClient = MockPDCClient(deliverParcelCall)

        val message =
            MessageFactory.buildOutgoing(createEndpointChannel(RecipientAddressType.PUBLIC))
        subject.send(message)
    }
}
