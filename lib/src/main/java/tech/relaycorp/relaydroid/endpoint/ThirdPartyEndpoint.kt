package tech.relaycorp.relaydroid.endpoint

import tech.relaycorp.relaydroid.Storage
import tech.relaycorp.relaydroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.RelaynetException
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import tech.relaycorp.relaynet.wrappers.x509.CertificateException

public sealed class ThirdPartyEndpoint(
    override val address: String
) : Endpoint {

    public val thirdPartyAddress: String get() = address

    public companion object {
        @Throws(PersistenceException::class)
        internal suspend fun load(
            firstPartyAddress: String, thirdPartyAddress: String
        ): ThirdPartyEndpoint? =
            PublicThirdPartyEndpoint.load(thirdPartyAddress)
                ?: PrivateThirdPartyEndpoint.load(firstPartyAddress, thirdPartyAddress)
    }
}

public class PrivateThirdPartyEndpoint(
    public val firstPartyAddress: String,
    thirdPartyAddress: String,
    public val authorization: Certificate,
    public val identity: Certificate
) : ThirdPartyEndpoint(thirdPartyAddress) {

    public companion object {

        @Throws(PersistenceException::class)
        public suspend fun load(
            firstPartyAddress: String, thirdPartyAddress: String
        ): PrivateThirdPartyEndpoint? {
            val key = "${firstPartyAddress}_$thirdPartyAddress"
            return Storage.thirdPartyAuthorization.get(key)?.let { auth ->
                Storage.thirdPartyIdentityCertificate.get(key)?.let { id ->
                    PrivateThirdPartyEndpoint(firstPartyAddress, thirdPartyAddress, auth, id)
                }
            }
        }

        @Throws(
            PersistenceException::class,
            UnknownFirstPartyEndpointException::class
        )
        public suspend fun importAuthorization(
            pda: Certificate, identity: Certificate
        ): PrivateThirdPartyEndpoint {
            val firstPartyAddress = pda.subjectPrivateAddress

            Storage.identityCertificate.get(firstPartyAddress)
                ?: throw UnknownFirstPartyEndpointException(
                    "First party endpoint $firstPartyAddress not registered"
                )

            try {
                pda.getCertificationPath(emptyList(), listOf(identity))
            } catch (e: CertificateException) {
                throw InvalidAuthorizationException("PDA was not issued by third-party endpoint", e)
            }

            val thirdPartyAddress = identity.subjectPrivateAddress

            val key = "${firstPartyAddress}_$thirdPartyAddress"
            Storage.thirdPartyAuthorization.set(key, pda)
            Storage.thirdPartyIdentityCertificate.set(key, identity)

            return PrivateThirdPartyEndpoint(firstPartyAddress, thirdPartyAddress, pda, identity)
        }
    }
}

public class PublicThirdPartyEndpoint(
    thirdPartyAddress: String,
    public val certificate: Certificate
) : ThirdPartyEndpoint(thirdPartyAddress) {

    public companion object {
        @Throws(PersistenceException::class)
        public suspend fun load(thirdPartyAddress: String): PublicThirdPartyEndpoint? =
            Storage.publicThirdPartyCertificate.get(thirdPartyAddress)?.let {
                PublicThirdPartyEndpoint(thirdPartyAddress, it)
            }

        @Throws(
            PersistenceException::class,
            CertificateException::class
        )
        public suspend fun import(certificate: Certificate): PublicThirdPartyEndpoint {
            certificate.validate()
            val thirdPartyAddress = certificate.subjectPrivateAddress
            Storage.publicThirdPartyCertificate.set(thirdPartyAddress, certificate)
            return PublicThirdPartyEndpoint(thirdPartyAddress, certificate)
        }
    }
}

public class UnknownThirdPartyEndpointException(message: String) : RelaynetException(message, null)
public class UnknownFirstPartyEndpointException(message: String) : RelaynetException(message, null)
public class InvalidAuthorizationException(message: String, cause: Throwable)
    : RelaynetException(message, cause)
