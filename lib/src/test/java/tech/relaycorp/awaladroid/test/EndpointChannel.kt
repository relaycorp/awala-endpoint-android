package tech.relaycorp.awaladroid.test

import tech.relaycorp.awaladroid.endpoint.FirstPartyEndpoint
import tech.relaycorp.awaladroid.endpoint.ThirdPartyEndpoint
import tech.relaycorp.relaynet.SessionKeyPair

internal data class EndpointChannel(
    val firstPartyEndpoint: FirstPartyEndpoint,
    val thirdPartyEndpoint: ThirdPartyEndpoint,
    val thirdPartySessionKeyPair: SessionKeyPair,
    val firstPartySessionKeyPair: SessionKeyPair,
)
