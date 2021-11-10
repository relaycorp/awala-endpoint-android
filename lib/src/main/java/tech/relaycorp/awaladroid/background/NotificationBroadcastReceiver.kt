package tech.relaycorp.awaladroid.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.relaycorp.awaladroid.Awala

internal class NotificationBroadcastReceiver : BroadcastReceiver() {

    internal var coroutineContext: CoroutineContext = Dispatchers.IO

    override fun onReceive(context: Context?, intent: Intent?) {
        CoroutineScope(coroutineContext).launch {
            Awala.getContextOrThrow().gatewayClient.checkForNewMessages()
        }
    }
}
