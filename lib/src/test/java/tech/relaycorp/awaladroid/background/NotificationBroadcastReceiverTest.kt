package tech.relaycorp.awaladroid.background

import android.content.Intent
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import tech.relaycorp.awaladroid.GatewayClientImpl
import tech.relaycorp.awaladroid.Awala

@RunWith(RobolectricTestRunner::class)
internal class NotificationBroadcastReceiverTest {
    @Test
    fun name() = runBlockingTest {
        val context = RuntimeEnvironment.systemContext
        Awala.setup(context)
        val gatewayClient = mock<GatewayClientImpl>()
        Awala.gatewayClientImpl = gatewayClient

        val receiver = NotificationBroadcastReceiver()
        receiver.coroutineContext = coroutineContext
        receiver.onReceive(context, Intent())

        verify(gatewayClient).checkForNewMessages()
    }
}
