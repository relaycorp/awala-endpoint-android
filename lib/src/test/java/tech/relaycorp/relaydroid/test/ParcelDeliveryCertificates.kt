package tech.relaycorp.relaydroid.test

import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.issueGatewayCertificate
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import java.time.ZonedDateTime

object ParcelDeliveryCertificates {
    private val startDate by lazy { ZonedDateTime.now().minusMinutes(1) }
    private val endDate by lazy { ZonedDateTime.now().plusHours(1) }

    val PRIVATE_GW by lazy {
        issueGatewayCertificate(
            KeyPairSet.PRIVATE_GW.public,
            KeyPairSet.PRIVATE_GW.private,
            endDate,
            validityStartDate = startDate
        )
    }

    val PRIVATE_ENDPOINT by lazy {
        issueEndpointCertificate(
            KeyPairSet.PRIVATE_ENDPOINT.public,
            KeyPairSet.PRIVATE_GW.private,
            endDate,
            validityStartDate = startDate
        )
    }
}
