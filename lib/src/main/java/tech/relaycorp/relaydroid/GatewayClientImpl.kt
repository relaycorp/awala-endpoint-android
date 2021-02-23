package tech.relaycorp.relaydroid

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.relaycorp.poweb.PoWebClient
import tech.relaycorp.relaydroid.background.ServiceInteractor
import tech.relaycorp.relaydroid.common.Logging.logger
import tech.relaycorp.relaydroid.messaging.IncomingMessage
import tech.relaycorp.relaydroid.messaging.OutgoingMessage
import tech.relaycorp.relaydroid.messaging.ReceiveMessages
import tech.relaycorp.relaydroid.messaging.SendMessage
import tech.relaycorp.relaynet.bindings.pdc.PDCClient
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistration
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistrationRequest
import java.security.KeyPair
import java.util.logging.Level
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

public class GatewayClientImpl
internal constructor(
    private val coroutineContext: CoroutineContext = Dispatchers.IO,
    private val serviceInteractorBuilder: () -> ServiceInteractor,
    private val pdcClientBuilder: () -> PDCClient =
        { PoWebClient.initLocal(port = Relaynet.POWEB_PORT) },
    private val sendMessage: SendMessage = SendMessage(),
    private val receiveMessages: ReceiveMessages = ReceiveMessages()
) {

    // Gateway

    private var gwServiceInteractor: ServiceInteractor? = null

    @Throws(CouldNotBindToGatewayException::class)
    public suspend fun bind() {
        withContext(coroutineContext) {
            if (gwServiceInteractor != null) return@withContext // Already connected

            gwServiceInteractor = serviceInteractorBuilder().apply {
                try {
                    bind(
                        Relaynet.GATEWAY_PACKAGE,
                        Relaynet.GATEWAY_SYNC_COMPONENT
                    )
                } catch (exp: ServiceInteractor.BindFailedException) {
                    throw CouldNotBindToGatewayException(
                        "Failed binding to Relaynet Gateway for registration",
                        exp
                    )
                }
            }
            delay(1_000) // Wait for server to start
        }
    }

    public fun unbind() {
        gwServiceInteractor?.unbind()
        gwServiceInteractor = null
    }

    // First-Party Endpoints

    internal suspend fun registerEndpoint(keyPair: KeyPair): PrivateNodeRegistration {
        val preAuthSerialized = preRegister()
        val request = PrivateNodeRegistrationRequest(keyPair.public, preAuthSerialized)
        val requestSerialized = request.serialize(keyPair.private)

        bind()

        return pdcClientBuilder().use {
            it.registerNode(requestSerialized)
        }
    }

    private suspend fun preRegister(): ByteArray {
        val interactor = serviceInteractorBuilder().apply {
            try {
                bind(
                    Relaynet.GATEWAY_PACKAGE,
                    Relaynet.GATEWAY_PRE_REGISTER_COMPONENT
                )
            } catch (exp: ServiceInteractor.BindFailedException) {
                throw CouldNotBindToGatewayException(
                    "Failed binding to Relaynet Gateway for pre-registration",
                    exp
                )
            }
        }

        return suspendCoroutine { cont ->
            val request = android.os.Message.obtain(null, PREREGISTRATION_REQUEST)
            CoroutineScope(coroutineContext).launch {
                interactor.sendMessage(request) { replyMessage ->
                    if (replyMessage.what != REGISTRATION_AUTHORIZATION) {
                        interactor.unbind()
                        cont.resumeWithException(Exception("Pre-registration failed"))
                        return@sendMessage
                    }
                    interactor.unbind()
                    cont.resume(replyMessage.data.getByteArray("auth")!!)
                }
            }
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
        val wasBound = gwServiceInteractor != null
        if (!wasBound) {
            try {
                bind()
            } catch (exp: CouldNotBindToGatewayException) {
                logger.log(Level.SEVERE, "Could not bind to gateway to receive new messages", exp)
                return
            }
        }

        receiveMessages
            .receive()
            .onEach(incomingMessageChannel::send)
            .launchIn(CoroutineScope(coroutineContext))

        if (!wasBound) unbind()
    }

    internal companion object {
        internal const val PREREGISTRATION_REQUEST = 1
        internal const val REGISTRATION_AUTHORIZATION = 2
    }
}

public open class GatewayRelaynetException(message: String, cause: Throwable? = null)
    : RelaynetException(message, cause)

public class CouldNotBindToGatewayException(message: String, cause: Throwable? = null)
    : GatewayRelaynetException(message, cause)
