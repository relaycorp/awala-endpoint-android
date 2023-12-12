package tech.relaycorp.awaladroid.background

import android.content.Intent
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import tech.relaycorp.awaladroid.test.MockContextTestCase

@RunWith(RobolectricTestRunner::class)
internal class IncomingParcelBroadcastReceiverTest : MockContextTestCase() {
    @Test
    fun name() =
        runTest {
            val receiver = IncomingParcelBroadcastReceiver()
            receiver.coroutineContext = coroutineContext
            receiver.onReceive(RuntimeEnvironment.getApplication(), Intent())
            advanceUntilIdle()
            verify(gatewayClient).checkForNewMessages()
        }
}
