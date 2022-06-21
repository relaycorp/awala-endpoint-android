package tech.relaycorp.awaladroid

import java.security.KeyPair
import java.util.logging.Level
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import tech.relaycorp.awaladroid.background.ServiceInteractor
import tech.relaycorp.awaladroid.common.Logging.logger
import tech.relaycorp.awaladroid.messaging.IncomingMessage
import tech.relaycorp.awaladroid.messaging.OutgoingMessage
import tech.relaycorp.awaladroid.messaging.ReceiveMessageException
import tech.relaycorp.awaladroid.messaging.ReceiveMessages
import tech.relaycorp.awaladroid.messaging.RejectedMessageException
import tech.relaycorp.awaladroid.messaging.SendMessage
import tech.relaycorp.awaladroid.messaging.SendMessageException
import tech.relaycorp.awaladroid.storage.persistence.PersistenceException
import tech.relaycorp.poweb.PoWebClient
import tech.relaycorp.relaynet.bindings.pdc.ClientBindingException
import tech.relaycorp.relaynet.bindings.pdc.PDCClient
import tech.relaycorp.relaynet.bindings.pdc.ServerException
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistration
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistrationRequest

/**
 * Private gateway client.
 */
public class GatewayClientImpl
internal constructor(
    private val coroutineContext: CoroutineContext = Dispatchers.IO,
    private val serviceInteractorBuilder: () -> ServiceInteractor,
    private val pdcClientBuilder: () -> PDCClient =
        { PoWebClient.initLocal(port = Awala.POWEB_PORT) },
    private val sendMessage: SendMessage = SendMessage(),
    private val receiveMessages: ReceiveMessages = ReceiveMessages()
) {

    // Gateway

    private var gwServiceInteractor: ServiceInteractor? = null

    /**
     * Bind to the gateway to be able to communicate with it.
     */
    @Throws(GatewayBindingException::class)
    public suspend fun bind() {
        withContext(coroutineContext) {
            if (gwServiceInteractor != null) return@withContext // Already connected

            gwServiceInteractor = serviceInteractorBuilder().apply {
                try {
                    bind(
                        Awala.GATEWAY_SYNC_ACTION,
                        Awala.GATEWAY_PACKAGE,
                        Awala.GATEWAY_SYNC_COMPONENT
                    )
                } catch (exp: ServiceInteractor.BindFailedException) {
                    throw GatewayBindingException(
                        "Failed binding to Awala Gateway for registration",
                        exp
                    )
                }
            }
            delay(1_000) // Wait for server to start
        }
    }

    /**
     * Unbind from the gateway.
     *
     * Make sure to call this when you no longer need to communicate with the gateway.
     */
    public fun unbind() {
        gwServiceInteractor?.unbind()
        gwServiceInteractor = null
    }

    // First-Party Endpoints

    @Throws(
        RegistrationFailedException::class,
        GatewayProtocolException::class
    )
    internal suspend fun registerEndpoint(keyPair: KeyPair): PrivateNodeRegistration =
        withContext(coroutineContext) {
            try {

                val preAuthSerialized = preRegister()
                val request = PrivateNodeRegistrationRequest(keyPair.public, preAuthSerialized)
                val requestSerialized = request.serialize(keyPair.private)

                bind()

                return@withContext pdcClientBuilder().use {
                    it.registerNode(requestSerialized)
                }
            } catch (exp: ServiceInteractor.BindFailedException) {
                throw RegistrationFailedException("Failed binding to gateway", exp)
            } catch (exp: ServiceInteractor.SendFailedException) {
                throw RegistrationFailedException("Failed communicating with gateway", exp)
            } catch (exp: ServerException) {
                throw RegistrationFailedException("Registration failed due to server", exp)
            } catch (exp: ClientBindingException) {
                throw GatewayProtocolException("Registration failed due to client", exp)
            } catch (exp: GatewayBindingException) {
                throw RegistrationFailedException("Failed binding to gateway", exp)
            }
        }

    @Throws(
        ServiceInteractor.BindFailedException::class,
        ServiceInteractor.SendFailedException::class,
        GatewayProtocolException::class
    )
    private suspend fun preRegister(): ByteArray {
        val interactor = serviceInteractorBuilder().apply {
            bind(
                Awala.GATEWAY_PRE_REGISTER_ACTION,
                Awala.GATEWAY_PACKAGE,
                Awala.GATEWAY_PRE_REGISTER_COMPONENT
            )
        }

        return suspendCoroutine { cont ->
            val request = android.os.Message.obtain(null, PREREGISTRATION_REQUEST)
            interactor.sendMessage(request) { replyMessage ->
                if (replyMessage.what != REGISTRATION_AUTHORIZATION) {
                    interactor.unbind()
                    cont.resumeWithException(
                        GatewayProtocolException("Pre-registration failed, received wrong reply")
                    )
                    return@sendMessage
                }
                interactor.unbind()
                cont.resume(replyMessage.data.getByteArray("auth")!!)
            }
        }
    }

    // Messaging

    @Throws(
        GatewayBindingException::class,
        GatewayProtocolException::class,
        SendMessageException::class,
        RejectedMessageException::class
    )
    public suspend fun sendMessage(message: OutgoingMessage) {
        if (gwServiceInteractor == null) {
            throw GatewayBindingException("Gateway not bound")
        }
        sendMessage.send(message)
    }

    private val incomingMessageChannel = MutableSharedFlow<IncomingMessage>(1)

    /**
     * Receive messages from the gateway.
     */
    public fun receiveMessages(): Flow<IncomingMessage> = incomingMessageChannel.asSharedFlow()

    // Internal

    internal suspend fun checkForNewMessages() {
        withContext(coroutineContext) {
            val wasAlreadyBound = gwServiceInteractor != null
            if (!wasAlreadyBound) {
                try {
                    bind()
                } catch (exp: GatewayBindingException) {
                    logger.log(
                        Level.SEVERE,
                        "Could not bind to gateway to receive new messages",
                        exp
                    )
                    return@withContext
                }
            }

            try {
                receiveMessages
                    .receive()
                    .collect(incomingMessageChannel::emit)
            } catch (exp: ReceiveMessageException) {
                logger.log(Level.SEVERE, "Could not receive new messages", exp)
            } catch (exp: GatewayProtocolException) {
                logger.log(Level.SEVERE, "Could not receive new messages", exp)
            } catch (exp: PersistenceException) {
                logger.log(Level.SEVERE, "Could not receive new messages", exp)
            }

            if (!wasAlreadyBound) unbind()
        }
    }

    internal companion object {
        internal const val PREREGISTRATION_REQUEST = 1
        internal const val REGISTRATION_AUTHORIZATION = 2
    }
}

/**
 * General class for all exceptions deriving from interactions with the gateway.
 */
public open class GatewayException(message: String, cause: Throwable? = null) :
    AwaladroidException(message, cause)

/**
 * Non-recoverable, protocol-level discrepancies when interacting with the gateway.
 */
public open class GatewayProtocolException(message: String, cause: Throwable? = null) :
    GatewayException(message, cause)

/**
 * Not bound or failure to bind to the gateway.
 */
public class GatewayBindingException(message: String, cause: Throwable? = null) :
    GatewayException(message, cause)

/**
 * Failure to register a first-party endpoint.
 */
public class RegistrationFailedException(message: String, cause: Throwable? = null) :
    GatewayException(message, cause)
