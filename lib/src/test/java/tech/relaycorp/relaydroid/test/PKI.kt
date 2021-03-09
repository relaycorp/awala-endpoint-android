package tech.relaycorp.relaydroid.test

import java.time.ZonedDateTime
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.testing.pki.KeyPairSet

/**
 * Identity certificate of a public endpoint.
 */
internal val PUBLIC_ENDPOINT_CERTIFICATE = issueEndpointCertificate(
    KeyPairSet.PDA_GRANTEE.public,
    KeyPairSet.PDA_GRANTEE.private,
    ZonedDateTime.now().plusDays(1)
)
