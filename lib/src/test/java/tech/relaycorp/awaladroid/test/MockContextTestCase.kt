package tech.relaycorp.awaladroid.test

import com.nhaarman.mockitokotlin2.mock
import org.junit.After
import org.junit.Before
import tech.relaycorp.awaladroid.AwalaContext
import tech.relaycorp.awaladroid.GatewayClientImpl
import tech.relaycorp.awaladroid.storage.StorageImpl
import tech.relaycorp.awaladroid.storage.mockStorage
import tech.relaycorp.relaynet.SessionKeyPair
import tech.relaycorp.relaynet.nodes.EndpointManager
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import tech.relaycorp.relaynet.testing.keystores.MockPrivateKeyStore
import tech.relaycorp.relaynet.testing.keystores.MockSessionPublicKeyStore

internal abstract class MockContextTestCase {
    protected open val gatewayClient: GatewayClientImpl = mock()
    protected open val storage: StorageImpl = mockStorage()
    protected val privateKeyStore: MockPrivateKeyStore = MockPrivateKeyStore()
    protected val sessionPublicKeystore: MockSessionPublicKeyStore = MockSessionPublicKeyStore()

    @Before
    fun setMockContext() {
        setAwalaContext(
            AwalaContext(
                storage,
                gatewayClient,
                EndpointManager(privateKeyStore, sessionPublicKeystore),
                privateKeyStore,
                sessionPublicKeystore,
            )
        )
    }

    @Before
    fun resetKeystores() {
        privateKeyStore.clear()
        sessionPublicKeystore.clear()
    }

    @After
    fun unsetContext(): Unit = unsetAwalaContext()

    protected suspend fun createEndpointChannel(
        thirdPartyEndpointType: RecipientAddressType
    ): EndpointChannel {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        privateKeyStore.saveIdentityKey(
            firstPartyEndpoint.identityPrivateKey,
            firstPartyEndpoint.identityCertificate,
        )

        val thirdPartyEndpoint = ThirdPartyEndpointFactory.build(thirdPartyEndpointType)

        val firstPartySessionKeyPair = SessionKeyPair.generate()
        privateKeyStore.saveSessionKey(
            firstPartySessionKeyPair.privateKey,
            firstPartySessionKeyPair.sessionKey.keyId,
            firstPartyEndpoint.privateAddress,
            thirdPartyEndpoint.privateAddress,
        )

        val thirdPartySessionKeyPair = SessionKeyPair.generate()
        sessionPublicKeystore.save(
            thirdPartySessionKeyPair.sessionKey,
            thirdPartyEndpoint.privateAddress
        )

        return EndpointChannel(
            firstPartyEndpoint,
            thirdPartyEndpoint,
            thirdPartySessionKeyPair,
            firstPartySessionKeyPair,
        )
    }
}
