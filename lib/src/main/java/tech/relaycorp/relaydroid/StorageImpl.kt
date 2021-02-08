package tech.relaycorp.relaydroid

import org.bouncycastle.jce.provider.BouncyCastleProvider
import tech.relaycorp.relaydroid.persistence.Persistence
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec

internal class StorageImpl
constructor(
    private val persistence: Persistence
) {

    private suspend fun getIdentityPrivateKey(endpoint: String) =
        persistence.get("$KEY_IDENTITY_PRIVATE_KEY$endpoint")?.toPrivateKey()

    private suspend fun setIdentityPrivateKey(endpoint: String, privateKey: PrivateKey) {
        persistence.set("$KEY_IDENTITY_PRIVATE_KEY$endpoint", privateKey.encoded)
    }

    private suspend fun deleteIdentityPrivateKey(endpoint: String) {
        persistence.delete("$KEY_IDENTITY_PRIVATE_KEY$endpoint")
    }

    suspend fun getIdentityKeyPair(endpoint: String) =
        getIdentityPrivateKey(endpoint)?.toKeyPair()

    suspend fun setIdentityKeyPair(endpoint: String, keyPair: KeyPair) {
        setIdentityPrivateKey(endpoint, keyPair.private)
    }

    suspend fun deleteIdentityKeyPair(endpoint: String) {
        deleteIdentityPrivateKey(endpoint)
    }

    suspend fun getIdentityCertificate(endpoint: String) =
        persistence.get("$KEY_IDENTITY_CERTIFICATE$endpoint")?.let { Certificate.deserialize(it) }

    suspend fun setIdentityCertificate(endpoint: String, certificate: Certificate) {
        persistence.set("$KEY_IDENTITY_CERTIFICATE$endpoint", certificate.serialize())
    }

    suspend fun deleteIdentityCertificate(endpoint: String) {
        persistence.delete("$KEY_IDENTITY_CERTIFICATE$endpoint")
    }

    suspend fun getGatewayCertificate() =
        persistence.get(KEY_GATEWAY_CERTIFICATE)?.let { Certificate.deserialize(it) }

    suspend fun setGatewayCertificate(certificate: Certificate) {
        persistence.set(KEY_GATEWAY_CERTIFICATE, certificate.serialize())
    }

    suspend fun deleteGatewayCertificate() {
        persistence.delete(KEY_GATEWAY_CERTIFICATE)
    }

    suspend fun listEndpoints() =
        persistence.list(KEY_IDENTITY_CERTIFICATE)
            .map { it.substring(KEY_IDENTITY_CERTIFICATE.length) }

    private fun ByteArray.toPrivateKey(): PrivateKey {
        val privateKeySpec = PKCS8EncodedKeySpec(this)
        val generator: KeyFactory = KeyFactory.getInstance(KEY_ALGORITHM, bouncyCastleProvider)
        return generator.generatePrivate(privateKeySpec)
    }

    private fun PrivateKey.toKeyPair(): KeyPair {
        val publicKeySpec =
            (this as RSAPrivateCrtKey).run { RSAPublicKeySpec(modulus, publicExponent) }
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, bouncyCastleProvider)
        val publicKey = keyFactory.generatePublic(publicKeySpec)
        return KeyPair(publicKey, this)
    }

    companion object {
        private const val KEY_ALGORITHM = "RSA"
        private val bouncyCastleProvider = BouncyCastleProvider()

        private const val KEY_IDENTITY_PRIVATE_KEY = "id_private_key_"
        private const val KEY_IDENTITY_CERTIFICATE = "id_certificate_"
        private const val KEY_GATEWAY_CERTIFICATE = "gateway_certificate"

    }
}
