package tech.relaycorp.awaladroid.messaging

import tech.relaycorp.relaynet.ramf.RAMFMessage

/**
 * A service message.
 */
public abstract class Message {
    public companion object {
        private const val PESSIMISTIC_CMS_ENVELOPEDDATA_OVERHEAD_OCTETS = 1024

        /**
         * The maximum size of the content of a message.
         */
        public const val MAX_CONTENT_SIZE: Int =
            RAMFMessage.MAX_PAYLOAD_LENGTH - PESSIMISTIC_CMS_ENVELOPEDDATA_OVERHEAD_OCTETS
    }
}
