package tech.relaycorp.awaladroid.endpoint

/**
 * Parcel delivery authorization for a third-party endpoint.
 */
public class ThirdPartyEndpointAuth(
    /**
     * Id of the third-party endpoint.
     */
    public val endpointId: String,
    /**
     * The authorization serialized.
     */
    public val auth: ByteArray,
)
