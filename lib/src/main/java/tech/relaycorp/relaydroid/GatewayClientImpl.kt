package tech.relaycorp.relaydroid

import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.Looper
import android.os.Messenger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import tech.relaycorp.poweb.PoWebClient
import tech.relaycorp.relaydroid.background.suspendBindService
import tech.relaycorp.relaydroid.messaging.IncomingMessage
import tech.relaycorp.relaydroid.messaging.OutgoingMessage
import tech.relaycorp.relaydroid.messaging.ReceiveMessages
import tech.relaycorp.relaydroid.messaging.SendMessage
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistrationRequest
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import java.security.KeyPair
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

public class GatewayClientImpl
internal constructor(
    private val context: Context,
    private val coroutineContext: CoroutineContext = Dispatchers.IO,
    private val sendMessage: SendMessage = SendMessage(),
    private val receiveMessages: ReceiveMessages = ReceiveMessages()
) {

    // Gateway

    private var syncConnection: ServiceConnection? = null

    public suspend fun bind() {
        withContext(coroutineContext) {
            if (syncConnection != null) return@withContext // Already connected

            val bindResult = context.suspendBindService(
                Relaynet.GATEWAY_PACKAGE,
                Relaynet.GATEWAY_SYNC_COMPONENT
            )
            syncConnection = bindResult.first
            delay(1_000) // Wait for server to start
        }
    }

    public fun unbind() {
        syncConnection?.let { context.unbindService(it) }
        syncConnection = null
    }

    // First-Party Endpoints

    internal suspend fun registerEndpoint(keyPair: KeyPair): Pair<Certificate, Certificate> {
        val preAuthSerialized = preRegister()
        val request = PrivateNodeRegistrationRequest(keyPair.public, preAuthSerialized)
        val requestSerialized = request.serialize(keyPair.private)

        bind()

        val poweb = PoWebClient.initLocal(port = Relaynet.POWEB_PORT)
        val pnr = poweb.registerNode(requestSerialized)
        return Pair(
            pnr.privateNodeCertificate,
            pnr.gatewayCertificate
        )
    }

    private suspend fun preRegister(): ByteArray {
        val bindResult = context.suspendBindService(
            Relaynet.GATEWAY_PACKAGE,
            Relaynet.GATEWAY_PRE_REGISTER_COMPONENT
        )
        val serviceConnection = bindResult.first
        val binder = bindResult.second

        return suspendCoroutine { cont ->
            val request = android.os.Message.obtain(null, PREREGISTRATION_REQUEST)
            request.replyTo = Messenger(object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: android.os.Message) {
                    if (msg.what != REGISTRATION_AUTHORIZATION) {
                        cont.resumeWithException(Exception("pre-register failed"))
                        context.unbindService(serviceConnection)
                        return
                    }
                    cont.resume(msg.data.getByteArray("auth")!!)
                    context.unbindService(serviceConnection)
                }
            })
            Messenger(binder).send(request)
        }
    }

    // Messaging

    public suspend fun sendMessage(message: OutgoingMessage) {
        sendMessage.send(message)
    }

    private val incomingMessageChannel = BroadcastChannel<IncomingMessage>(1)
    public fun receiveMessages(): Flow<IncomingMessage> = incomingMessageChannel.asFlow()

    // Internal

    // TODO: Review bind checks and uniformise gateway exceptions
    internal suspend fun checkForNewMessages() {
        val wasBound = syncConnection != null
        if (!wasBound) bind()

        receiveMessages
            .receive()
            .onEach(incomingMessageChannel::send)
            .launchIn(CoroutineScope(coroutineContext))

        if (!wasBound) unbind()
    }

    private companion object {
        private const val PREREGISTRATION_REQUEST = 1
        private const val REGISTRATION_AUTHORIZATION = 2
    }
}
