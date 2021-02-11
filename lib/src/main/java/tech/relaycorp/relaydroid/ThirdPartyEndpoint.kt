package tech.relaycorp.relaydroid

public sealed class ThirdPartyEndpoint(override val address: String) : Endpoint
public class PrivateThirdPartyEndpoint(address: String) : ThirdPartyEndpoint(address)
public class PublicThirdPartyEndpoint(address: String) : ThirdPartyEndpoint(address)
