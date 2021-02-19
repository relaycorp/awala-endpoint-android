package tech.relaycorp.relaydroid.background

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import tech.relaycorp.relaydroid.common.Logging.logger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class ServiceInteractor(
    private val context: Context
) {

    private var serviceConnection: ServiceConnection? = null
    private var binder: IBinder? = null

    suspend fun bind(packageName: String, componentName: String) =
        suspendCoroutine<Unit> { cont ->
            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(p0: ComponentName?, binder: IBinder) {
                    logger.info("Connected to service $packageName - $componentName")
                    serviceConnection = this
                    this@ServiceInteractor.binder = binder
                    cont.resume(Unit)
                }

                override fun onServiceDisconnected(p0: ComponentName?) {
                    logger.info("Disconnected to service $packageName - $componentName")
                }
            }
            context.bindService(
                Intent().setComponent(ComponentName(packageName, componentName)),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }

    fun unbind() {
        serviceConnection?.let { context.unbindService(it) }
        binder = null
    }

    suspend fun sendMessage(message: Message, reply: ((Message) -> Unit)? = null) {
        val binder = binder ?: return

        val looper = Looper.myLooper() ?: Looper.getMainLooper()
        reply?.let {
            message.replyTo = Messenger(object : Handler(looper) {
                override fun handleMessage(msg: Message) {
                    reply(msg)
                }
            })
        }
        Messenger(binder).send(message)
    }
}
