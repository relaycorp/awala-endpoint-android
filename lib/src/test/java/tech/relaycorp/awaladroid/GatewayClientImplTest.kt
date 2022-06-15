package tech.relaycorp.awaladroid

import android.os.Bundle
import android.os.Message
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tech.relaycorp.awaladroid.background.ServiceInteractor
import tech.relaycorp.awaladroid.messaging.IncomingMessage
import tech.relaycorp.awaladroid.messaging.ReceiveMessageException
import tech.relaycorp.awaladroid.messaging.ReceiveMessages
import tech.relaycorp.awaladroid.messaging.RejectedMessageException
import tech.relaycorp.awaladroid.messaging.SendMessage
import tech.relaycorp.awaladroid.messaging.SendMessageException
import tech.relaycorp.awaladroid.test.MessageFactory
import tech.relaycorp.awaladroid.test.MockContextTestCase
import tech.relaycorp.relaynet.bindings.pdc.ClientBindingException
import tech.relaycorp.relaynet.bindings.pdc.ServerConnectionException
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistration
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistrationAuthorization
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import tech.relaycorp.relaynet.testing.pdc.MockPDCClient
import tech.relaycorp.relaynet.testing.pdc.RegisterNodeCall
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath

@RunWith(RobolectricTestRunner::class)
internal class GatewayClientImplTest : MockContextTestCase() {

    private lateinit var pdcClient: MockPDCClient
    private val coroutineScope = TestScope()
    private val serviceInteractor = mock<ServiceInteractor>()
    private val sendMessage = mock<SendMessage>()
    private val receiveMessages = mock<ReceiveMessages>()

    override val gatewayClient = GatewayClientImpl(
        coroutineScope.coroutineContext, { serviceInteractor }, { pdcClient }, sendMessage,
        receiveMessages
    )

    // Binding

    @Test
    fun bind_successful() = coroutineScope.runTest {
        gatewayClient.bind()

        verify(serviceInteractor).bind(
            Awala.GATEWAY_SYNC_ACTION,
            Awala.GATEWAY_PACKAGE,
            Awala.GATEWAY_SYNC_COMPONENT
        )
    }

    @Test
    fun secondBindIsSkipped() = coroutineScope.runTest {
        gatewayClient.bind()
        gatewayClient.bind()

        verify(serviceInteractor, times(1))
            .bind(Awala.GATEWAY_SYNC_ACTION, Awala.GATEWAY_PACKAGE, Awala.GATEWAY_SYNC_COMPONENT)
    }

    @Test
    fun reBind_successful() = coroutineScope.runTest {
        gatewayClient.bind()
        gatewayClient.unbind()
        gatewayClient.bind()

        verify(serviceInteractor, times(2))
            .bind(Awala.GATEWAY_SYNC_ACTION, Awala.GATEWAY_PACKAGE, Awala.GATEWAY_SYNC_COMPONENT)
    }

    @Test(expected = GatewayBindingException::class)
    fun bind_unsuccessful() = coroutineScope.runTest {
        whenever(serviceInteractor.bind(any(), any(), any()))
            .thenThrow(ServiceInteractor.BindFailedException(""))

        gatewayClient.bind()
    }

    // Registration

    @Test
    internal fun registerEndpoint_successful() = coroutineScope.runTest {
        val replyMessage = buildAuthorizationReplyMessage()
        whenever(serviceInteractor.sendMessage(any(), any())).thenAnswer {
            it.getArgument<((Message) -> Unit)?>(1)(replyMessage)
        }

        val pnr = PrivateNodeRegistration(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW)
        pdcClient = MockPDCClient(RegisterNodeCall(Result.success(pnr)))

        val result = gatewayClient.registerEndpoint(KeyPairSet.PRIVATE_ENDPOINT)

        verify(serviceInteractor)
            .bind(
                Awala.GATEWAY_PRE_REGISTER_ACTION,
                Awala.GATEWAY_PACKAGE,
                Awala.GATEWAY_PRE_REGISTER_COMPONENT
            )
        verify(serviceInteractor)
            .bind(Awala.GATEWAY_SYNC_ACTION, Awala.GATEWAY_PACKAGE, Awala.GATEWAY_SYNC_COMPONENT)

        assertEquals(PDACertPath.PRIVATE_ENDPOINT, result.privateNodeCertificate)
        assertEquals(PDACertPath.PRIVATE_GW, result.gatewayCertificate)
    }

    @Test(expected = RegistrationFailedException::class)
    internal fun registerEndpoint_withFailedPreRegisterBind() = coroutineScope.runTest {
        whenever(serviceInteractor.sendMessage(any(), any()))
            .thenThrow(ServiceInteractor.BindFailedException(""))

        gatewayClient.registerEndpoint(KeyPairSet.PRIVATE_ENDPOINT)
    }

    @Test(expected = RegistrationFailedException::class)
    internal fun registerEndpoint_withFailedPreRegisterSend() = coroutineScope.runTest {
        whenever(serviceInteractor.sendMessage(any(), any()))
            .thenThrow(ServiceInteractor.SendFailedException(Exception()))

        gatewayClient.registerEndpoint(KeyPairSet.PRIVATE_ENDPOINT)
    }

    @Test(expected = RegistrationFailedException::class)
    internal fun registerEndpoint_withFailedRegistrationDueToServer() =
        coroutineScope.runTest {
            val replyMessage = buildAuthorizationReplyMessage()
            whenever(serviceInteractor.sendMessage(any(), any())).thenAnswer {
                it.getArgument<((Message) -> Unit)?>(1)(replyMessage)
            }

            pdcClient =
                MockPDCClient(RegisterNodeCall(Result.failure(ServerConnectionException(""))))

            gatewayClient.registerEndpoint(KeyPairSet.PRIVATE_ENDPOINT)
        }

    @Test(expected = GatewayProtocolException::class)
    internal fun registerEndpoint_withFailedRegistrationDueToClient() =
        coroutineScope.runTest {
            val replyMessage = buildAuthorizationReplyMessage()
            whenever(serviceInteractor.sendMessage(any(), any())).thenAnswer {
                it.getArgument<((Message) -> Unit)?>(1)(replyMessage)
            }

            pdcClient = MockPDCClient(RegisterNodeCall(Result.failure(ClientBindingException(""))))

            gatewayClient.registerEndpoint(KeyPairSet.PRIVATE_ENDPOINT)
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
    fun sendMessage_successful() = coroutineScope.runTest {
        val message =
            MessageFactory.buildOutgoing(createEndpointChannel(RecipientAddressType.PUBLIC))

        gatewayClient.bind()
        gatewayClient.sendMessage(message)
    }

    @Test(expected = GatewayBindingException::class)
    fun sendMessage_withoutBind() = coroutineScope.runTest {
        val message =
            MessageFactory.buildOutgoing(createEndpointChannel(RecipientAddressType.PUBLIC))

        gatewayClient.sendMessage(message)
    }

    @Test(expected = SendMessageException::class)
    fun sendMessage_unsuccessful() = coroutineScope.runTest {
        whenever(sendMessage.send(any())).thenThrow(SendMessageException(""))
        val message =
            MessageFactory.buildOutgoing(createEndpointChannel(RecipientAddressType.PUBLIC))

        gatewayClient.bind()
        gatewayClient.sendMessage(message)
    }

    @Test(expected = GatewayProtocolException::class)
    fun sendMessage_unsuccessfulDueToClient() = coroutineScope.runTest {
        whenever(sendMessage.send(any())).thenThrow(GatewayProtocolException(""))
        val message =
            MessageFactory.buildOutgoing(createEndpointChannel(RecipientAddressType.PUBLIC))

        gatewayClient.bind()
        gatewayClient.sendMessage(message)
    }

    @Test(expected = RejectedMessageException::class)
    fun sendMessage_unsuccessfulDueToRejection() = coroutineScope.runTest {
        whenever(sendMessage.send(any())).thenThrow(RejectedMessageException(""))
        val message =
            MessageFactory.buildOutgoing(createEndpointChannel(RecipientAddressType.PUBLIC))

        gatewayClient.bind()
        gatewayClient.sendMessage(message)
    }

    @Test
    fun checkForNewMessages_bindsIfNeeded() = coroutineScope.runTest {
        whenever(receiveMessages.receive()).thenReturn(emptyFlow())

        gatewayClient.checkForNewMessages()

        verify(serviceInteractor)
            .bind(
                eq(Awala.GATEWAY_SYNC_ACTION),
                eq(Awala.GATEWAY_PACKAGE),
                eq(Awala.GATEWAY_SYNC_COMPONENT)
            )
        verify(serviceInteractor)
            .unbind()
    }

    @Test
    fun checkForNewMessages_doesNotRebind() = coroutineScope.runTest {
        whenever(receiveMessages.receive()).thenReturn(emptyFlow())

        gatewayClient.bind()
        gatewayClient.checkForNewMessages()

        verify(serviceInteractor, times(1)).bind(any(), any(), any())
    }

    @Test
    fun checkForNewMessages_relaysIncomingMessages() = coroutineScope.runTest {
        val message = MessageFactory.buildIncoming()
        whenever(receiveMessages.receive()).thenReturn(flowOf(message))

        val messagesReceived = mutableListOf<IncomingMessage>()
        CoroutineScope(UnconfinedTestDispatcher()).launch {
            gatewayClient.receiveMessages().toCollection(messagesReceived)
        }

        gatewayClient.checkForNewMessages()

        assertEquals(listOf(message), messagesReceived)
    }

    @Test
    fun checkForNewMessages_handlesReceiveException() = coroutineScope.runTest {
        whenever(receiveMessages.receive()).thenReturn(flow { throw ReceiveMessageException("") })

        gatewayClient.checkForNewMessages()
    }

    @Test
    fun checkForNewMessages_handlesProtocolException() = coroutineScope.runTest {
        whenever(receiveMessages.receive()).thenReturn(flow { throw GatewayProtocolException("") })

        gatewayClient.checkForNewMessages()
    }
}
