package tech.relaycorp.relaydroid.endpoint

public class AuthorizationBundle(
    public val pdaSerialized: ByteArray,
    public val pdaChainSerialized: List<ByteArray>
)
