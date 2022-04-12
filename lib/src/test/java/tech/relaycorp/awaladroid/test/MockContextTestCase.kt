package tech.relaycorp.awaladroid.test

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.After
import org.junit.Before
import org.mockito.internal.util.MockUtil
import tech.relaycorp.awaladroid.AwalaContext
import tech.relaycorp.awaladroid.GatewayClientImpl
import tech.relaycorp.awaladroid.endpoint.ChannelManager
import tech.relaycorp.awaladroid.endpoint.FirstPartyEndpoint
import tech.relaycorp.awaladroid.storage.StorageImpl
import tech.relaycorp.awaladroid.storage.mockStorage
import tech.relaycorp.relaynet.SessionKeyPair
import tech.relaycorp.relaynet.nodes.EndpointManager
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import tech.relaycorp.relaynet.testing.keystores.MockCertificateStore
import tech.relaycorp.relaynet.testing.keystores.MockPrivateKeyStore
import tech.relaycorp.relaynet.testing.keystores.MockSessionPublicKeyStore

internal abstract class MockContextTestCase {
    protected open val gatewayClient: GatewayClientImpl = mock()
    protected open val storage: StorageImpl = mockStorage()
    protected val privateKeyStore: MockPrivateKeyStore = MockPrivateKeyStore()
    protected val sessionPublicKeystore: MockSessionPublicKeyStore = MockSessionPublicKeyStore()
    protected val certificateStore: MockCertificateStore = MockCertificateStore()
    private val channelManager: ChannelManager = mock()

    @Before
    fun setMockContext() {
        setAwalaContext(
            AwalaContext(
                storage,
                gatewayClient,
                EndpointManager(privateKeyStore, sessionPublicKeystore),
                channelManager,
                privateKeyStore,
                sessionPublicKeystore,
                certificateStore,
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
        val firstPartyEndpoint = createFirstPartyEndpoint()

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

    protected suspend fun createFirstPartyEndpoint(): FirstPartyEndpoint {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        privateKeyStore.saveIdentityKey(
            firstPartyEndpoint.identityPrivateKey,
        )

        val certificate = firstPartyEndpoint.identityCertificate
        certificateStore.save(
            certificate,
            firstPartyEndpoint.identityCertificateChain,
            certificate.issuerCommonName
        )

        if (MockUtil.isMock(storage)) {
            whenever(storage.gatewayPrivateAddress.get(firstPartyEndpoint.privateAddress))
                .thenReturn(certificate.issuerCommonName)
        } else {
            storage.gatewayPrivateAddress.set(
                firstPartyEndpoint.privateAddress,
                certificate.issuerCommonName
            )
        }

        return firstPartyEndpoint
    }
}
