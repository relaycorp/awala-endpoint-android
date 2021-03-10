package tech.relaycorp.relaydroid.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.relaycorp.relaydroid.GatewayClient

internal class NotificationBroadcastReceiver : BroadcastReceiver() {

    internal var coroutineContext: CoroutineContext = Dispatchers.IO

    override fun onReceive(context: Context?, intent: Intent?) {
        CoroutineScope(coroutineContext).launch {
            GatewayClient.checkForNewMessages()
        }
    }
}
