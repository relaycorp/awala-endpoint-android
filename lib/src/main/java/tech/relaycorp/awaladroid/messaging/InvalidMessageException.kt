package tech.relaycorp.awaladroid.messaging

import tech.relaycorp.awaladroid.AwaladroidException

/**
 * Exception thrown when an incoming or outgoing service message is invalid.
 */
public class InvalidMessageException(
    message: String,
    cause: Throwable,
) : AwaladroidException(message, cause)
