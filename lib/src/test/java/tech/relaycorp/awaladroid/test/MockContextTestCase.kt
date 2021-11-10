package tech.relaycorp.awaladroid.test

import com.nhaarman.mockitokotlin2.mock
import org.junit.After
import org.junit.Before
import tech.relaycorp.awaladroid.AwalaContext
import tech.relaycorp.awaladroid.GatewayClientImpl
import tech.relaycorp.awaladroid.storage.StorageImpl
import tech.relaycorp.awaladroid.storage.mockStorage
import tech.relaycorp.relaynet.nodes.EndpointManager
import tech.relaycorp.relaynet.testing.keystores.MockPrivateKeyStore
import tech.relaycorp.relaynet.testing.keystores.MockSessionPublicKeyStore

internal abstract class MockContextTestCase {
    protected val gateway: GatewayClientImpl = mock<GatewayClientImpl>()
    protected val storage: StorageImpl = mockStorage()
    protected val privateKeyStore: MockPrivateKeyStore = MockPrivateKeyStore()
    protected val sessionPublicKeystore: MockSessionPublicKeyStore = MockSessionPublicKeyStore()

    @Before
    fun setMockContext() {
        setAwalaContext(
            AwalaContext(
                storage,
                gateway,
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
}
