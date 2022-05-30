package tech.relaycorp.awaladroid.messaging

import java.util.logging.Level
import tech.relaycorp.awaladroid.Awala
import tech.relaycorp.awaladroid.SetupPendingException
import tech.relaycorp.awaladroid.common.Logging.logger
import tech.relaycorp.awaladroid.endpoint.FirstPartyEndpoint
import tech.relaycorp.awaladroid.endpoint.InvalidAuthorizationException
import tech.relaycorp.awaladroid.endpoint.PrivateThirdPartyEndpoint
import tech.relaycorp.awaladroid.endpoint.PublicThirdPartyEndpoint
import tech.relaycorp.awaladroid.endpoint.ThirdPartyEndpoint
import tech.relaycorp.awaladroid.endpoint.UnknownFirstPartyEndpointException
import tech.relaycorp.awaladroid.endpoint.UnknownThirdPartyEndpointException
import tech.relaycorp.awaladroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.keystores.MissingKeyException
import tech.relaycorp.relaynet.messages.InvalidMessageException
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.pki.CertificationPath
import tech.relaycorp.relaynet.pki.CertificationPathException
import tech.relaycorp.relaynet.wrappers.cms.EnvelopedDataException

/**
 * An incoming service message.
 *
 * @property type The type of the service message (e.g., "application/vnd.relaynet.ping-v1.ping").
 * @property content The contents of the service message.
 * @property senderEndpoint The third-party endpoint that created the message.
 * @property recipientEndpoint The first-party endpoint that should receive the message.
 * @property ack The function to call as soon as the message has been processed.
 */
public class IncomingMessage internal constructor(
    public val type: String,
    public val content: ByteArray,
    public val senderEndpoint: ThirdPartyEndpoint,
    public val recipientEndpoint: FirstPartyEndpoint,
    public val ack: suspend () -> Unit
) : Message() {

    internal companion object {
        private const val PDA_PATH_TYPE = "application/vnd+relaycorp.awala.pda-path"

        @Throws(
            UnknownFirstPartyEndpointException::class,
            UnknownThirdPartyEndpointException::class,
            PersistenceException::class,
            EnvelopedDataException::class,
            InvalidMessageException::class,
            SetupPendingException::class,
        )
        internal suspend fun build(parcel: Parcel, ack: suspend () -> Unit): IncomingMessage? {
            val recipientEndpoint = FirstPartyEndpoint.load(parcel.recipientAddress)
                ?: throw UnknownFirstPartyEndpointException(
                    "Unknown first-party endpoint ${parcel.recipientAddress}"
                )

            val sender = ThirdPartyEndpoint.load(
                parcel.recipientAddress,
                parcel.senderCertificate.subjectPrivateAddress,
            ) ?: throw UnknownThirdPartyEndpointException(
                "Unknown third-party endpoint " +
                    "${parcel.senderCertificate.subjectPrivateAddress} " +
                    "for first-party endpoint ${parcel.recipientAddress}"
            )

            val context = Awala.getContextOrThrow()

            val serviceMessage = try {
                context.endpointManager.unwrapMessagePayload(parcel)
            } catch (e: MissingKeyException) {
                throw UnknownThirdPartyEndpointException(
                    "Missing third-party endpoint session keys"
                )
            }
            if (serviceMessage.type == PDA_PATH_TYPE) {
                processPDAPath(serviceMessage.content, sender, recipientEndpoint)
                ack()
                return null
            }
            return IncomingMessage(
                type = serviceMessage.type,
                content = serviceMessage.content,
                senderEndpoint = sender,
                recipientEndpoint = recipientEndpoint,
                ack = ack
            )
        }

        private suspend fun processPDAPath(
            pdaPathSerialized: ByteArray,
            senderEndpoint: ThirdPartyEndpoint,
            recipientEndpoint: FirstPartyEndpoint,
        ) {
            if (senderEndpoint is PublicThirdPartyEndpoint) {
                logger.info(
                    "Ignoring PDA path from public endpoint ${senderEndpoint.privateAddress} " +
                        "(${senderEndpoint.publicAddress})"
                )
                return
            }
            val pdaPath = try {
                CertificationPath.deserialize(pdaPathSerialized)
            } catch (exc: CertificationPathException) {
                logger.log(
                    Level.INFO,
                    "Ignoring malformed PDA path for ${recipientEndpoint.privateAddress} " +
                        "from ${senderEndpoint.privateAddress}",
                    exc,
                )
                return
            }

            try {
                (senderEndpoint as PrivateThirdPartyEndpoint).updatePDAPath(pdaPath)
            } catch (exc: InvalidAuthorizationException) {
                logger.log(
                    Level.INFO,
                    "Ignoring invalid PDA path for ${recipientEndpoint.privateAddress} " +
                        "from ${senderEndpoint.privateAddress}",
                    exc,
                )
                return
            }
            logger.info(
                "Updated PDA path from ${senderEndpoint.privateAddress} for " +
                    recipientEndpoint.privateAddress
            )
        }
    }
}
