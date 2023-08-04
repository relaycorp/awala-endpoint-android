package tech.relaycorp.awaladroid.common

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.RSAPublicKeySpec

internal fun PrivateKey.toKeyPair(): KeyPair =
    KeyPair(toPublicKey(), this)

internal fun PrivateKey.toPublicKey(): PublicKey {
    val rsaPrivateKey = this as RSAPrivateCrtKey
    val keyFactory = KeyFactory.getInstance("RSA", BouncyCastleProvider())
    val publicKeySpec = RSAPublicKeySpec(rsaPrivateKey.modulus, rsaPrivateKey.publicExponent)
    return keyFactory.generatePublic(publicKeySpec)
}
