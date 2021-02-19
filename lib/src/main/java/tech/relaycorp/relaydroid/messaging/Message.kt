package tech.relaycorp.relaydroid.messaging

import tech.relaycorp.relaydroid.Endpoint
import tech.relaycorp.relaydroid.FirstPartyEndpoint
import tech.relaycorp.relaydroid.PrivateThirdPartyEndpoint
import tech.relaycorp.relaydroid.PublicThirdPartyEndpoint
import tech.relaycorp.relaydroid.RelaynetException
import tech.relaycorp.relaydroid.ThirdPartyEndpoint
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.ramf.RAMFException
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import java.time.Duration
import java.time.ZonedDateTime

public abstract class Message(
    public val id: MessageId,
    public val payload: ByteArray,
    senderEndpoint: Endpoint,
    recipientEndpoint: Endpoint,
    public val creationDate: ZonedDateTime = ZonedDateTime.now(),
    public val expirationDate: ZonedDateTime = maxExpirationDate()
) {

    internal val ttl get() = Duration.between(creationDate, expirationDate).seconds.toInt()

    internal companion object {
        internal fun maxExpirationDate() = ZonedDateTime.now().plusDays(30)
    }
}
