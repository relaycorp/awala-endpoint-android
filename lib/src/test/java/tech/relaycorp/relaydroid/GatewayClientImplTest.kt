package tech.relaycorp.relaydroid

import android.os.Bundle
import android.os.Message
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tech.relaycorp.relaydroid.background.ServiceInteractor
import tech.relaycorp.relaydroid.messaging.IncomingMessage
import tech.relaycorp.relaydroid.messaging.ReceiveMessages
import tech.relaycorp.relaydroid.messaging.ReceiveMessagesException
import tech.relaycorp.relaydroid.messaging.RejectedMessageException
import tech.relaycorp.relaydroid.messaging.SendMessage
import tech.relaycorp.relaydroid.messaging.SendMessageException
import tech.relaycorp.relaydroid.test.MessageFactory
import tech.relaycorp.relaynet.bindings.pdc.ClientBindingException
import tech.relaycorp.relaynet.bindings.pdc.ServerConnectionException
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistration
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistrationAuthorization
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import tech.relaycorp.relaynet.testing.pdc.MockPDCClient
import tech.relaycorp.relaynet.testing.pdc.RegisterNodeCall
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
internal class GatewayClientImplTest {

    private lateinit var pdcClient: MockPDCClient
    private val coroutineScope = TestCoroutineScope()
    private val serviceInteractor = mock<ServiceInteractor>()
    private val sendMessage = mock<SendMessage>()
    private val receiveMessages = mock<ReceiveMessages>()
    private val subject = GatewayClientImpl(
        coroutineScope.coroutineContext, { serviceInteractor }, { pdcClient }, sendMessage,
        receiveMessages
    )

    // Binding

    @Test
    fun bind_successful() = coroutineScope.runBlockingTest {
        subject.bind()

        verify(serviceInteractor).bind(Relaynet.GATEWAY_PACKAGE, Relaynet.GATEWAY_SYNC_COMPONENT)
    }

    @Test
    fun secondBindIsSkipped() = coroutineScope.runBlockingTest {
        subject.bind()
        subject.bind()

        verify(serviceInteractor, times(1))
            .bind(Relaynet.GATEWAY_PACKAGE, Relaynet.GATEWAY_SYNC_COMPONENT)
    }

    @Test
    fun reBind_successful() = coroutineScope.runBlockingTest {
        subject.bind()
        subject.unbind()
        subject.bind()

        verify(serviceInteractor, times(2))
            .bind(Relaynet.GATEWAY_PACKAGE, Relaynet.GATEWAY_SYNC_COMPONENT)
    }

    @Test(expected = GatewayBindingException::class)
    fun bind_unsuccessful() = coroutineScope.runBlockingTest {
        whenever(serviceInteractor.bind(any(), any()))
            .thenThrow(ServiceInteractor.BindFailedException(""))

        subject.bind()
    }

    // Registration

    @Test
    internal fun registerEndpoint_successful() = coroutineScope.runBlockingTest {
        val replyMessage = buildAuthorizationReplyMessage()
        whenever(serviceInteractor.sendMessage(any(), any())).thenAnswer {
            it.getArgument<((Message) -> Unit)?>(1)(replyMessage)
        }

        val pnr = PrivateNodeRegistration(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW)
        pdcClient = MockPDCClient(RegisterNodeCall(Result.success(pnr)))

        val result = subject.registerEndpoint(KeyPairSet.PRIVATE_ENDPOINT)

        verify(serviceInteractor)
            .bind(Relaynet.GATEWAY_PACKAGE, Relaynet.GATEWAY_PRE_REGISTER_COMPONENT)
        verify(serviceInteractor)
            .bind(Relaynet.GATEWAY_PACKAGE, Relaynet.GATEWAY_SYNC_COMPONENT)

        assertEquals(PDACertPath.PRIVATE_ENDPOINT, result.privateNodeCertificate)
        assertEquals(PDACertPath.PRIVATE_GW, result.gatewayCertificate)
    }

    @Test(expected = RegistrationFailedException::class)
    internal fun registerEndpoint_withFailedPreRegisterBind() = coroutineScope.runBlockingTest {
        whenever(serviceInteractor.sendMessage(any(), any()))
            .thenThrow(ServiceInteractor.BindFailedException(""))

        subject.registerEndpoint(KeyPairSet.PRIVATE_ENDPOINT)
    }

    @Test(expected = RegistrationFailedException::class)
    internal fun registerEndpoint_withFailedPreRegisterSend() = coroutineScope.runBlockingTest {
        whenever(serviceInteractor.sendMessage(any(), any()))
            .thenThrow(ServiceInteractor.SendFailedException(Exception()))

        subject.registerEndpoint(KeyPairSet.PRIVATE_ENDPOINT)
    }

    @Test(expected = RegistrationFailedException::class)
    internal fun registerEndpoint_withFailedRegistrationDueToServer() =
        coroutineScope.runBlockingTest {
            val replyMessage = buildAuthorizationReplyMessage()
            whenever(serviceInteractor.sendMessage(any(), any())).thenAnswer {
                it.getArgument<((Message) -> Unit)?>(1)(replyMessage)
            }

            pdcClient = MockPDCClient(RegisterNodeCall(Result.failure(ServerConnectionException(""))))

            subject.registerEndpoint(KeyPairSet.PRIVATE_ENDPOINT)
        }

    @Test(expected = GatewayProtocolException::class)
    internal fun registerEndpoint_withFailedRegistrationDueToClient() =
        coroutineScope.runBlockingTest {
            val replyMessage = buildAuthorizationReplyMessage()
            whenever(serviceInteractor.sendMessage(any(), any())).thenAnswer {
                it.getArgument<((Message) -> Unit)?>(1)(replyMessage)
            }

            pdcClient = MockPDCClient(RegisterNodeCall(Result.failure(ClientBindingException(""))))

            subject.registerEndpoint(KeyPairSet.PRIVATE_ENDPOINT)
        }

    private fun buildPnra() = PrivateNodeRegistrationAuthorization(
        ZonedDateTime.now().plusDays(1),
        PDACertPath.PRIVATE_GW.serialize()
    )

    private fun buildAuthorizationReplyMessage(): Message {
        val pnra = buildPnra()
        val pnraSerialized = pnra.serialize(KeyPairSet.PRIVATE_GW.private)
        val replyMessage = Message.obtain(null, GatewayClientImpl.REGISTRATION_AUTHORIZATION)
        replyMessage.data = Bundle().also { it.putByteArray("auth", pnraSerialized) }
        return replyMessage
    }

    // Messaging

    @Test
    fun sendMessage_successful() = coroutineScope.runBlockingTest {
        val message = MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC)

        subject.bind()
        subject.sendMessage(message)
    }

    @Test(expected = GatewayBindingException::class)
    fun sendMessage_withoutBind() = coroutineScope.runBlockingTest {
        val message = MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC)

        subject.sendMessage(message)
    }

    @Test(expected = SendMessageException::class)
    fun sendMessage_unsuccessful() = coroutineScope.runBlockingTest {
        whenever(sendMessage.send(any())).thenThrow(SendMessageException(""))
        val message = MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC)

        subject.bind()
        subject.sendMessage(message)
    }

    @Test(expected = GatewayProtocolException::class)
    fun sendMessage_unsuccessfulDueToClient() = coroutineScope.runBlockingTest {
        whenever(sendMessage.send(any())).thenThrow(GatewayProtocolException(""))
        val message = MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC)

        subject.bind()
        subject.sendMessage(message)
    }

    @Test(expected = RejectedMessageException::class)
    fun sendMessage_unsuccessfulDueToRejection() = coroutineScope.runBlockingTest {
        whenever(sendMessage.send(any())).thenThrow(RejectedMessageException(""))
        val message = MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC)

        subject.bind()
        subject.sendMessage(message)
    }

    @Test
    fun checkForNewMessages_bindsIfNeeded() = coroutineScope.runBlockingTest {
        whenever(receiveMessages.receive()).thenReturn(emptyFlow())

        subject.checkForNewMessages()

        verify(serviceInteractor)
            .bind(eq(Relaynet.GATEWAY_PACKAGE), eq(Relaynet.GATEWAY_SYNC_COMPONENT))
        verify(serviceInteractor)
            .unbind()
    }

    @Test
    fun checkForNewMessages_doesNotRebind() = coroutineScope.runBlockingTest {
        whenever(receiveMessages.receive()).thenReturn(emptyFlow())

        subject.bind()
        subject.checkForNewMessages()

        verify(serviceInteractor, times(1)).bind(any(), any())
    }

    @Test
    fun checkForNewMessages_relaysIncomingMessages() = coroutineScope.runBlockingTest {
        val message = MessageFactory.buildIncoming()
        whenever(receiveMessages.receive()).thenReturn(flowOf(message))

        val messagesReceived = mutableListOf<IncomingMessage>()
        TestCoroutineScope().launch {
            subject.receiveMessages().toCollection(messagesReceived)
        }

        subject.checkForNewMessages()

        assertEquals(listOf(message), messagesReceived)
    }

    @Test
    fun checkForNewMessages_handlesReceiveException() = coroutineScope.runBlockingTest {
        whenever(receiveMessages.receive()).thenReturn(flow { throw ReceiveMessagesException("") })

        subject.checkForNewMessages()
    }

    @Test
    fun checkForNewMessages_handlesProtocolException() = coroutineScope.runBlockingTest {
        whenever(receiveMessages.receive()).thenReturn(flow { throw GatewayProtocolException("") })

        subject.checkForNewMessages()
    }
}
