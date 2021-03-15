package tech.relaycorp.awaladroid.endpoint

/**
 * Parcel Delivery Authorization (PDA) and support data for the grantee to use the PDA.
 */
public class AuthorizationBundle(
    /**
     * The ASN.1 DER encoding of the PDA.
     */
    public val pdaSerialized: ByteArray,

    /**
     * The ASN.1 DER encoding of each certificate in the PDA chain (excluding the PDA itself).
     */
    public val pdaChainSerialized: List<ByteArray>
)
