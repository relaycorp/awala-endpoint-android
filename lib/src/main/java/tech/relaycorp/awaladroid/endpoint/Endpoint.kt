package tech.relaycorp.awaladroid.endpoint

/**
 * Awala endpoint.
 */
public abstract class Endpoint(public val privateAddress: String) {
    /**
     * The private or public address of a private or public endpoint, respectively.
     *
     * This is the same as [privateAddress] if the endpoint is private.
     */
    public abstract val address: String
}
