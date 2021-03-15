package tech.relaycorp.awaladroid.endpoint

import tech.relaycorp.relaynet.wrappers.x509.Certificate

/**
 * Relaynet endpoint.
 */
public abstract class Endpoint(
    internal val identityCertificate: Certificate
) {
    /**
     * The private address of the endpoint.
     */
    public val privateAddress: String get() = identityCertificate.subjectPrivateAddress

    /**
     * The private or public address of a private or public endpoint, respectively.
     *
     * This is the same as [privateAddress] if the endpoint is private.
     */
    public abstract val address: String
}
