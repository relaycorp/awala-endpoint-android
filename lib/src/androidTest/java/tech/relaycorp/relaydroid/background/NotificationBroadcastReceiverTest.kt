package tech.relaycorp.relaydroid.background

import android.content.Intent
import androidx.test.rule.ServiceTestRule
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Rule
import org.junit.Test
import tech.relaycorp.relaydroid.GatewayClientI
import tech.relaycorp.relaydroid.Relaynet
import tech.relaycorp.relaydroid.test.TestAndroidProvider.context

class NotificationBroadcastReceiverTest {

    @get:Rule
    val rule = ServiceTestRule()

    @Test
    fun name() = runBlockingTest {
        Relaynet.setup(context)
        val gatewayClient = mock<GatewayClientI>()
        Relaynet.gatewayClientImpl = gatewayClient

        val receiver = NotificationBroadcastReceiver()
        receiver.coroutineContext = coroutineContext
        receiver.onReceive(context, Intent())

        verify(gatewayClient).checkForNewMessages()
    }
}
