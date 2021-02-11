package tech.relaycorp.relaydroid

import kotlinx.coroutines.flow.Flow
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import java.security.KeyPair

public interface GatewayClientI {

    public suspend fun bind()
    public fun unbind()

    public suspend fun registerEndpoint(keyPair: KeyPair): Pair<Certificate, Certificate>

    public suspend fun sendMessage(message: OutgoingMessage)
    public fun receiveMessages(): Flow<IncomingMessage>
    public suspend fun checkForNewMessages()

}
