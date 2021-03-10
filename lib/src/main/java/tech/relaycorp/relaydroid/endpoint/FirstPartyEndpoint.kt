package tech.relaycorp.relaydroid.endpoint

import java.security.KeyPair
import java.security.PublicKey
import java.time.ZonedDateTime
import tech.relaycorp.relaydroid.GatewayClient
import tech.relaycorp.relaydroid.GatewayProtocolException
import tech.relaycorp.relaydroid.RegistrationFailedException
import tech.relaycorp.relaydroid.RelaydroidException
import tech.relaycorp.relaydroid.Storage
import tech.relaycorp.relaydroid.storage.persistence.PersistenceException
import tech.relaycorp.relaynet.issueDeliveryAuthorization
import tech.relaycorp.relaynet.wrappers.KeyException
import tech.relaycorp.relaynet.wrappers.deserializeRSAPublicKey
import tech.relaycorp.relaynet.wrappers.generateRSAKeyPair
import tech.relaycorp.relaynet.wrappers.privateAddress
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import tech.relaycorp.relaynet.wrappers.x509.CertificateException

public class FirstPartyEndpoint
internal constructor(
    internal val keyPair: KeyPair,
    internal val identityCertificate: Certificate,
    internal val gatewayCertificate: Certificate
) : Endpoint {

    public override val address: String get() = keyPair.public.privateAddress

    public val publicKey: PublicKey get() = keyPair.public

    internal val pdaChain: List<Certificate> get() = listOf(identityCertificate, gatewayCertificate)

    @Throws(CertificateException::class)
    public fun issueAuthorization(
        thirdPartyEndpoint: PublicThirdPartyEndpoint,
        expiryDate: ZonedDateTime
    ): AuthorizationBundle {
        return issueAuthorization(
            thirdPartyEndpoint.identityCertificate.subjectPublicKey,
            expiryDate
        )
    }

    @Throws(CertificateException::class)
    public fun issueAuthorization(
        thirdPartyEndpointPublicKeySerialized: ByteArray,
        expiryDate: ZonedDateTime
    ): AuthorizationBundle {
        val thirdPartyEndpointPublicKey = try {
            thirdPartyEndpointPublicKeySerialized.deserializeRSAPublicKey()
        } catch (exc: KeyException) {
            throw AuthorizationIssuanceException(
                "PDA grantee public key is not a valid RSA public key",
                exc
            )
        }
        return issueAuthorization(thirdPartyEndpointPublicKey, expiryDate)
    }

    @Throws(CertificateException::class)
    private fun issueAuthorization(
        thirdPartyEndpointPublicKey: PublicKey,
        expiryDate: ZonedDateTime
    ): AuthorizationBundle {
        val pda = issueDeliveryAuthorization(
            subjectPublicKey = thirdPartyEndpointPublicKey,
            issuerPrivateKey = keyPair.private,
            validityEndDate = expiryDate,
            issuerCertificate = identityCertificate
        )
        return AuthorizationBundle(
            pda.serialize(),
            pdaChain.map { it.serialize() }
        )
    }

    @Throws(PersistenceException::class)
    public suspend fun delete() {
        Storage.identityKeyPair.delete(address)
        Storage.identityCertificate.delete(address)
    }

    @Throws(PersistenceException::class)
    private suspend fun store() {
        Storage.identityKeyPair.set(address, keyPair)
        Storage.identityCertificate.set(address, identityCertificate)
        Storage.gatewayCertificate.set(gatewayCertificate)
    }

    public companion object {
        @Throws(
            RegistrationFailedException::class,
            GatewayProtocolException::class,
            PersistenceException::class
        )
        public suspend fun register(): FirstPartyEndpoint {
            val keyPair = generateRSAKeyPair()
            val registration = GatewayClient.registerEndpoint(keyPair)
            val endpoint = FirstPartyEndpoint(
                keyPair,
                registration.privateNodeCertificate,
                registration.gatewayCertificate
            )
            endpoint.store()
            return endpoint
        }

        @Throws(PersistenceException::class)
        public suspend fun load(address: String): FirstPartyEndpoint? {
            return Storage.identityKeyPair.get(address)?.let { keyPair ->
                Storage.identityCertificate.get(address)?.let { certificate ->
                    Storage.gatewayCertificate.get()?.let { gwCertificate ->
                        FirstPartyEndpoint(
                            keyPair,
                            certificate,
                            gwCertificate
                        )
                    }
                }
            }
        }
    }
}

public class AuthorizationIssuanceException(message: String, cause: Throwable) :
    RelaydroidException(message, cause)
