package tech.relaycorp.relaydroid.storage

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.KeyPair
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec

// TODO: Test
internal object KeyPairSerializer {

    internal fun serialize(keyPair: KeyPair) = keyPair.private.encoded

    internal fun deserialize(data: ByteArray): KeyPair {
        val privateKeySpec = PKCS8EncodedKeySpec(data)
        val generator: KeyFactory = KeyFactory.getInstance(KEY_ALGORITHM, bouncyCastleProvider)
        val privateKey = generator.generatePrivate(privateKeySpec) as RSAPrivateCrtKey
        val publicKeySpec = privateKey.run { RSAPublicKeySpec(modulus, publicExponent) }
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, bouncyCastleProvider)
        val publicKey = keyFactory.generatePublic(publicKeySpec)
        return KeyPair(publicKey, privateKey)
    }

    private const val KEY_ALGORITHM = "RSA"
    private val bouncyCastleProvider = BouncyCastleProvider()
}
