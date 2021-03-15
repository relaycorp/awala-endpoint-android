package tech.relaycorp.awaladroid.background

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import tech.relaycorp.awaladroid.common.Logging.logger

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
                        unbind()
                        cont.resumeWithException(BindFailedException("Service disconnected"))
                    }
                }

                override fun onBindingDied(name: ComponentName?) {
                    if (!isResumed) {
                        isResumed = true
                        unbind()
                        cont.resumeWithException(BindFailedException("Binding died"))
                    }
                }

                override fun onNullBinding(name: ComponentName?) {
                    if (!isResumed) {
                        isResumed = true
                        unbind()
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

    @Throws(BindFailedException::class, SendFailedException::class)
    fun sendMessage(message: Message, reply: ((Message) -> Unit)? = null) {
        val binder = binder ?: throw BindFailedException("Service not bound")

        val looper = Looper.myLooper() ?: Looper.getMainLooper()
        reply?.let {
            message.replyTo = Messenger(object : Handler(looper) {
                override fun handleMessage(msg: Message) {
                    reply(msg)
                }
            })
        }
        try {
            Messenger(binder).send(message)
        } catch (exp: RemoteException) {
            throw SendFailedException(exp)
        }
    }

    class BindFailedException(message: String) : Exception(message)
    class SendFailedException(throwable: Throwable) : Exception(throwable)
}
