package tech.relaycorp.awaladroid

/**
 * Base class for all exceptions in this library.
 */
public abstract class RelaydroidException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
