package tech.relaycorp.relaydroid

import android.os.Bundle
import android.os.Message
import android.os.RemoteException
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tech.relaycorp.relaydroid.background.ServiceInteractor
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistration
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistrationAuthorization
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
    private val subject = GatewayClientImpl(
        coroutineScope.coroutineContext, { serviceInteractor }, { pdcClient }
    )

    @Test
    fun bind() = coroutineScope.runBlockingTest {
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
    fun reBind() = coroutineScope.runBlockingTest {
        subject.bind()
        subject.unbind()
        subject.bind()

        verify(serviceInteractor, times(2))
            .bind(Relaynet.GATEWAY_PACKAGE, Relaynet.GATEWAY_SYNC_COMPONENT)
    }

    @Test(expected = CouldNotBindToGatewayException::class)
    fun bindUnsuccessful() = coroutineScope.runBlockingTest {
        whenever(serviceInteractor.bind(any(), any()))
            .thenThrow(ServiceInteractor.BindFailedException(""))

        subject.bind()
    }

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

    @Test(expected = Exception::class)
    internal fun registerEndpoint_withFailedPreRegister() = coroutineScope.runBlockingTest {
        whenever(serviceInteractor.sendMessage(any(), any())).thenThrow(Exception(""))

        subject.registerEndpoint(KeyPairSet.PRIVATE_ENDPOINT)
    }

    @Test(expected = RemoteException::class)
    internal fun registerEndpoint_withFailedRegistration() = coroutineScope.runBlockingTest {
        val replyMessage = buildAuthorizationReplyMessage()
        whenever(serviceInteractor.sendMessage(any(), any())).thenAnswer {
            it.getArgument<((Message) -> Unit)?>(1)(replyMessage)
        }

        pdcClient = MockPDCClient(RegisterNodeCall(Result.failure(RemoteException(""))))

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
}
