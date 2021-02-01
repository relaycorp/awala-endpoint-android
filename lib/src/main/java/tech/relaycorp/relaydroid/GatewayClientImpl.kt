package tech.relaycorp.relaydroid

import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.Looper
import android.os.Messenger
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import tech.relaycorp.poweb.PoWebClient
import tech.relaycorp.relaydroid.background.suspendBindService
import tech.relaycorp.relaynet.bindings.pdc.Signer
import tech.relaycorp.relaynet.bindings.pdc.StreamingMode
import tech.relaycorp.relaynet.issueDeliveryAuthorization
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistrationRequest
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import java.security.KeyPair
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class GatewayClientImpl(
    private val context: Context
) : GatewayClientI {

    // Gateway

    private var syncConnection: ServiceConnection? = null

    override suspend fun bind() {
        withContext(Dispatchers.IO) {
            if (syncConnection != null) return@withContext // Already connected

            val bindResult = context.suspendBindService(
                Relaynet.GATEWAY_PACKAGE,
                Relaynet.GATEWAY_SYNC_COMPONENT
            )
            syncConnection = bindResult.first
            delay(1_000) // Wait for server to start
        }
    }

    override fun unbind() {
        syncConnection?.let { context.unbindService(it) }
        syncConnection = null
    }

    // First-Party Endpoints

    override suspend fun registerEndpoint(keyPair: KeyPair): Pair<Certificate, Certificate> {
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

    override suspend fun sendMessage(message: OutgoingMessage) {
        withContext(Dispatchers.IO) {
            val senderCertificate = getParcelDeliveryAuthorization(
                message.senderEndpoint,
                FirstPartyEndpoint.load(message.receiverEndpoint.address)!!
            )

            val parcel = Parcel(
                recipientAddress = message.receiverEndpoint.address,
                payload = message.message,
                senderCertificate = senderCertificate,
                messageId = message.id.value,
                creationDate = message.creationDate,
                ttl = Duration.between(
                    message.creationDate,
                    message.expirationDate
                ).seconds.toInt(),
                senderCertificateChain = setOf(
                    Storage.getCertificate(message.receiverEndpoint.address)!!,
                    Storage.getGatewayCertificate()!!
                )
            )

            val senderPrivateKey = Storage.getKeyPair(message.senderEndpoint.address)!!.private
            return@withContext try {
                PoWebClient.initLocal(Relaynet.POWEB_PORT)
                    .deliverParcel(
                        parcel.serialize(senderPrivateKey),
                        Signer(
                            senderCertificate,
                            senderPrivateKey
                        )
                    )
                true
            } catch (e: Exception) {
                Log.e("SendMessage", "Error sending message", e)
                false
            }
        }

    }

    private val incomingMessageChannel = BroadcastChannel<IncomingMessage>(1)
    override fun receiveMessages(): Flow<IncomingMessage> = incomingMessageChannel.asFlow()

    // Internal

    override suspend fun checkForNewMessages() {
        val wasBound = syncConnection != null
        if (!wasBound) bind()

        val poweb = PoWebClient.initLocal(Relaynet.POWEB_PORT)

        val nonceSigners = Storage
            .listEndpoints()
            .map { endpoint ->
                Signer(
                    Storage.getCertificate(endpoint)!!,
                    Storage.getKeyPair(endpoint)!!.private
                )
            }
            .toTypedArray()

        poweb
            .collectParcels(nonceSigners, StreamingMode.CloseUponCompletion)
            .collect { parcelCollection ->

                val parcel = Parcel.deserialize(parcelCollection.parcelSerialized)

                incomingMessageChannel.send(
                    IncomingMessage(
                        id = MessageId(parcel.id),
                        message = parcel.payload,
                        senderEndpoint = PrivateThirdPartyEndpoint(parcel.senderCertificate.subjectPrivateAddress),
                        receiverEndpoint = FirstPartyEndpoint.load(parcel.recipientAddress)!!,
                        creationDate = parcel.creationDate,
                        expiryDate = parcel.expiryDate,
                        ack = { parcelCollection.ack() }
                    )
                )
            }

        poweb.close()

        if (!wasBound) unbind()
    }

    // This only works because both endpoints are FirstPartyEndpoint
    private suspend fun getParcelDeliveryAuthorization(
        senderEndpoint: FirstPartyEndpoint,
        receiverEndpoint: FirstPartyEndpoint
    ): Certificate {

        val senderKeyPair = Storage.getKeyPair(senderEndpoint.address)!!
        val receiverKeyPair = Storage.getKeyPair(receiverEndpoint.address)!!
        val receiverCertificate = Storage.getCertificate(receiverEndpoint.address)!!


        return issueDeliveryAuthorization(
            senderKeyPair.public,
            receiverKeyPair.private,
            receiverCertificate.expiryDate,
            receiverCertificate,
            receiverCertificate.startDate
        )
    }

    companion object {
        private const val PREREGISTRATION_REQUEST = 1
        private const val REGISTRATION_AUTHORIZATION = 2
    }
}
