package tech.relaycorp.relaydroid

import org.bouncycastle.asn1.x500.style.BCStyle
import tech.relaycorp.relaydroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.RelaynetException
import tech.relaycorp.relaynet.issueDeliveryAuthorization
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import tech.relaycorp.relaynet.wrappers.x509.CertificateException
import java.security.PublicKey
import java.time.ZonedDateTime

public sealed class ThirdPartyEndpoint(
    override val thirdPartyAddress: String
) : Endpoint {

    public companion object {

        // Private

        @Throws(PersistenceException::class)
        public suspend fun loadPrivate(
            firstPartyAddress: String, thirdPartyAddress: String
        ): PrivateThirdPartyEndpoint? {
            val key = "${firstPartyAddress}_$thirdPartyAddress"
            return Storage.privateThirdPartyAuthorization.get(key)?.let {
                PrivateThirdPartyEndpoint(firstPartyAddress, thirdPartyAddress, it)
            }
        }

        @Throws(
            PersistenceException::class,
            InvalidFirstPartyEndpointAddressException::class
        )
        public suspend fun importPrivateAuthorization(pda: Certificate): PrivateThirdPartyEndpoint {
            val firstPartyAddress = pda.subjectPrivateAddress

            Storage.identityCertificate.get(firstPartyAddress)
                ?: throw InvalidFirstPartyEndpointAddressException(
                    "First party endpoint $firstPartyAddress not registered"
                )

            val thirdPartyAddress =
                pda.certificateHolder.subject.getRDNs(BCStyle.CN)
                    .first().first.value.toString()

            val key = "${firstPartyAddress}_$thirdPartyAddress"
            Storage.privateThirdPartyAuthorization.set(key, pda)

            return PrivateThirdPartyEndpoint(firstPartyAddress, thirdPartyAddress, pda)
        }

        @Throws(CertificateException::class)
        public fun issueAuthorization(
            firstPartyEndpoint: FirstPartyEndpoint,
            privateThirdPartyPublicKey: PublicKey,
            expiryDate: ZonedDateTime
        ): Certificate =
            issueDeliveryAuthorization(
                subjectPublicKey = privateThirdPartyPublicKey,
                issuerPrivateKey = firstPartyEndpoint.keyPair.private,
                validityEndDate = expiryDate,
                issuerCertificate = firstPartyEndpoint.certificate
            )

        // Public

        @Throws(PersistenceException::class)
        public suspend fun loadPublic(thirdPartyAddress: String): PublicThirdPartyEndpoint? =
            Storage.publicThirdPartyCertificate.get(thirdPartyAddress)?.let {
                PublicThirdPartyEndpoint(thirdPartyAddress, it)
            }

        @Throws(PersistenceException::class)
        public suspend fun importPublicEndpointCertificate(
            thirdPartyAddress: String, certificate: Certificate
        ): PublicThirdPartyEndpoint {
            Storage.publicThirdPartyCertificate.set(thirdPartyAddress, certificate)
            return PublicThirdPartyEndpoint(thirdPartyAddress, certificate)
        }

        // Helpers

        @Throws(PersistenceException::class)
        internal suspend fun load(
            firstPartyAddress: String, thirdPartyAddress: String
        ): ThirdPartyEndpoint? =
            if (isPublicAddress(thirdPartyAddress)) {
                loadPublic(thirdPartyAddress)
            } else {
                loadPrivate(firstPartyAddress, thirdPartyAddress)
            }

        private fun isPublicAddress(address: String) = address.contains(":")
    }
}

public class PrivateThirdPartyEndpoint(
    public val firstPartyAddress: String,
    thirdPartyAddress: String,
    public val authorization: Certificate
) : ThirdPartyEndpoint(thirdPartyAddress)

public class PublicThirdPartyEndpoint(
    thirdPartyAddress: String,
    public val certificate: Certificate
) : ThirdPartyEndpoint(thirdPartyAddress)

public class InvalidFirstPartyEndpointAddressException(message: String)
    : RelaynetException(message, null)
