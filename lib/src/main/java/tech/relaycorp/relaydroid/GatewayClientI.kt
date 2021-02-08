package tech.relaycorp.relaydroid

import kotlinx.coroutines.flow.Flow
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import java.security.KeyPair

interface GatewayClientI {

    suspend fun bind()
    fun unbind()

    suspend fun registerEndpoint(keyPair: KeyPair): Pair<Certificate, Certificate>

    suspend fun sendMessage(message: OutgoingMessage)
    fun receiveMessages(): Flow<IncomingMessage>
    suspend fun checkForNewMessages()

}
