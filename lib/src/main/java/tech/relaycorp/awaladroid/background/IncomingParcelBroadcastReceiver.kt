package tech.relaycorp.awaladroid.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.relaycorp.awaladroid.Awala
import tech.relaycorp.awaladroid.common.Logging.logger
import java.util.logging.Level

internal class IncomingParcelBroadcastReceiver : BroadcastReceiver() {

    internal var coroutineContext: CoroutineContext = Dispatchers.IO

    override fun onReceive(context: Context?, intent: Intent?) {
        logger.log(Level.INFO, "IncomingParcelBroadcastReceiver onReceive")
        CoroutineScope(coroutineContext).launch {
            Awala.getContextOrThrow().gatewayClient.checkForNewMessages()
        }
    }
}
