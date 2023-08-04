package tech.relaycorp.awaladroid.messaging

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
import tech.relaycorp.relaynet.InvalidNodeConnectionParams
import tech.relaycorp.relaynet.PrivateEndpointConnParams
import tech.relaycorp.relaynet.keystores.MissingKeyException
import tech.relaycorp.relaynet.messages.InvalidMessageException
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.wrappers.cms.EnvelopedDataException
import java.util.logging.Level

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
    public val ack: suspend () -> Unit,
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
            val recipientEndpoint = FirstPartyEndpoint.load(parcel.recipient.id)
                ?: throw UnknownFirstPartyEndpointException(
                    "Unknown first-party endpoint ${parcel.recipient.id}",
                )

            val sender = ThirdPartyEndpoint.load(
                parcel.recipient.id,
                parcel.senderCertificate.subjectId,
            ) ?: throw UnknownThirdPartyEndpointException(
                "Unknown third-party endpoint " +
                    "${parcel.senderCertificate.subjectId} " +
                    "for first-party endpoint ${parcel.recipient.id}",
            )

            val context = Awala.getContextOrThrow()

            val serviceMessage = try {
                context.endpointManager.unwrapMessagePayload(parcel)
            } catch (e: MissingKeyException) {
                throw UnknownThirdPartyEndpointException(
                    "Missing third-party endpoint session keys",
                )
            }
            if (serviceMessage.type == PDA_PATH_TYPE) {
                processConnectionParams(serviceMessage.content, sender, recipientEndpoint)
                ack()
                return null
            }
            return IncomingMessage(
                type = serviceMessage.type,
                content = serviceMessage.content,
                senderEndpoint = sender,
                recipientEndpoint = recipientEndpoint,
                ack = ack,
            )
        }

        private suspend fun processConnectionParams(
            paramsSerialized: ByteArray,
            senderEndpoint: ThirdPartyEndpoint,
            recipientEndpoint: FirstPartyEndpoint,
        ) {
            if (senderEndpoint is PublicThirdPartyEndpoint) {
                logger.info(
                    "Ignoring connection params from public endpoint ${senderEndpoint.nodeId} " +
                        "(${senderEndpoint.internetAddress})",
                )
                return
            }
            val params = try {
                PrivateEndpointConnParams.deserialize(paramsSerialized)
            } catch (exc: InvalidNodeConnectionParams) {
                logger.log(
                    Level.INFO,
                    "Ignoring malformed connection params for ${recipientEndpoint.nodeId} " +
                        "from ${senderEndpoint.nodeId}",
                    exc,
                )
                return
            }

            try {
                (senderEndpoint as PrivateThirdPartyEndpoint).updateParams(params)
            } catch (exc: InvalidAuthorizationException) {
                logger.log(
                    Level.INFO,
                    "Ignoring invalid connection params for ${recipientEndpoint.nodeId} " +
                        "from ${senderEndpoint.nodeId}",
                    exc,
                )
                return
            }
            logger.info(
                "Updated connection params from ${senderEndpoint.nodeId} for " +
                    recipientEndpoint.nodeId,
            )
        }
    }
}
