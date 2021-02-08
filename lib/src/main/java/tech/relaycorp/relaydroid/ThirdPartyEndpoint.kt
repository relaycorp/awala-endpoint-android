package tech.relaycorp.relaydroid

sealed class ThirdPartyEndpoint(override val address: String) : Endpoint
class PrivateThirdPartyEndpoint(address: String) : ThirdPartyEndpoint(address)
class PublicThirdPartyEndpoint(address: String) : ThirdPartyEndpoint(address)
