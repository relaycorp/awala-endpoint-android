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
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal class ServiceInteractor(
    private val context: Context
) {

    private var serviceConnection: ServiceConnection? = null
    private var binder: IBinder? = null

    @Throws(BindFailedException::class)
    suspend fun bind(packageName: String, componentName: String) =
        suspendCoroutine<Unit> { cont ->
            var isResumed = false

            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(p0: ComponentName?, binder: IBinder) {
                    logger.info("Connected to service $packageName - $componentName")
                    serviceConnection = this
                    this@ServiceInteractor.binder = binder
                    if (!isResumed) {
                        isResumed = true
                        cont.resume(Unit)
                    }
                }

                override fun onServiceDisconnected(p0: ComponentName?) {
                    if (!isResumed) {
                        isResumed = true
                        cont.resumeWithException(BindFailedException("Service disconnected"))
                    }
                }

                override fun onBindingDied(name: ComponentName?) {
                    if (!isResumed) {
                        isResumed = true
                        cont.resumeWithException(BindFailedException("Binding died"))
                    }
                }

                override fun onNullBinding(name: ComponentName?) {
                    if (!isResumed) {
                        isResumed = true
                        cont.resumeWithException(BindFailedException("Null binding"))
                    }
                }
            }

            val bindWasSuccessful = context.bindService(
                Intent().setComponent(ComponentName(packageName, componentName)),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
            if (!bindWasSuccessful) cont.resumeWithException(BindFailedException("Binding failed"))
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

    class BindFailedException(message: String) : Exception(message)
}
