package tech.relaycorp.relaydroid.background

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import tech.relaycorp.relaydroid.GatewayClientI
import tech.relaycorp.relaydroid.Relaynet

@RunWith(RobolectricTestRunner::class)
internal class NotificationBroadcastReceiverTest {
    @Test
    fun name() = runBlockingTest {
        val context = RuntimeEnvironment.systemContext
        Relaynet.setup(context)
        val gatewayClient = mock<GatewayClientI>()
        Relaynet.gatewayClientImpl = gatewayClient

        val receiver = NotificationBroadcastReceiver()
        receiver.coroutineContext = coroutineContext
        receiver.onReceive(context, Intent())

        verify(gatewayClient).checkForNewMessages()
    }
}
