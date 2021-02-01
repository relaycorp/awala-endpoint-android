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

    private suspend fun getPrivateKey(endpoint: String) =
        persistence.get("private_key_$endpoint")?.toPrivateKey()

    private suspend fun setPrivateKey(endpoint: String, privateKey: PrivateKey) {
        persistence.set("private_key_$endpoint", privateKey.encoded)
    }

    private suspend fun deletePrivateKey(endpoint: String) {
        persistence.delete("private_key_$endpoint")
    }

    suspend fun getKeyPair(endpoint: String) =
        getPrivateKey(endpoint)?.toKeyPair()

    suspend fun setKeyPair(endpoint: String, keyPair: KeyPair) {
        setPrivateKey(endpoint, keyPair.private)
    }

    suspend fun deleteKeyPair(endpoint: String) {
        deletePrivateKey(endpoint)
    }

    suspend fun getCertificate(endpoint: String) =
        persistence.get("certificate_$endpoint")?.let { Certificate.deserialize(it) }

    suspend fun setCertificate(endpoint: String, certificate: Certificate) {
        persistence.set("certificate_$endpoint", certificate.serialize())
    }

    suspend fun deleteCertificate(endpoint: String) {
        persistence.delete("certificate_$endpoint")
    }

    suspend fun getGatewayCertificate() =
        persistence.get("gateway_certificate")?.let { Certificate.deserialize(it) }

    suspend fun setGatewayCertificate(certificate: Certificate) {
        persistence.set("gateway_certificate", certificate.serialize())
    }

    suspend fun deleteGatewayCertificate() {
        persistence.delete("gateway_certificate")
    }

    suspend fun listEndpoints() =
        persistence.list("certificate_")
            .map { it.substring("certificate_".length) }

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
    }
}
