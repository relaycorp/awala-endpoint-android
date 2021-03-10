package tech.relaycorp.relaydroid.endpoint

/**
 * Relaynet endpoint.
 */
public interface Endpoint {
    /**
     * The private or public address of the endpoint, depending on whether it's public/private.
     */
    public val address: String
}
