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
import tech.relaycorp.awaladroid.endpoint.HandleGatewayCertificateChange
import tech.relaycorp.awaladroid.endpoint.PrivateThirdPartyEndpointData
import tech.relaycorp.awaladroid.endpoint.PublicThirdPartyEndpointData
import tech.relaycorp.awaladroid.endpoint.ThirdPartyEndpoint
import tech.relaycorp.awaladroid.storage.StorageImpl
import tech.relaycorp.awaladroid.storage.mockStorage
import tech.relaycorp.relaynet.SessionKey
import tech.relaycorp.relaynet.SessionKeyPair
import tech.relaycorp.relaynet.nodes.EndpointManager
import tech.relaycorp.relaynet.pki.CertificationPath
import tech.relaycorp.relaynet.testing.keystores.MockCertificateStore
import tech.relaycorp.relaynet.testing.keystores.MockPrivateKeyStore
import tech.relaycorp.relaynet.testing.keystores.MockSessionPublicKeyStore
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath

internal abstract class MockContextTestCase {
    protected open val gatewayClient: GatewayClientImpl = mock()
    protected open val storage: StorageImpl = mockStorage()
    protected val privateKeyStore: MockPrivateKeyStore = MockPrivateKeyStore()
    protected val sessionPublicKeystore: MockSessionPublicKeyStore = MockSessionPublicKeyStore()
    protected val certificateStore: MockCertificateStore = MockCertificateStore()
    protected val handleGatewayCertificateChange: HandleGatewayCertificateChange = mock()

    // We'd ideally use the real thing but we can't use SharedPreferences in unit tests
    protected val channelManager: ChannelManager = mock()

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
                handleGatewayCertificateChange,
            ),
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
        thirdPartyEndpointType: RecipientAddressType,
    ): EndpointChannel {
        val firstPartyEndpoint = createFirstPartyEndpoint()

        val thirdPartySessionKeyPair = SessionKeyPair.generate()
        val thirdPartyEndpoint =
            createThirdPartyEndpoint(
                thirdPartyEndpointType,
                thirdPartySessionKeyPair.sessionKey,
                firstPartyEndpoint,
            )

        val firstPartySessionKeyPair = SessionKeyPair.generate()
        privateKeyStore.saveSessionKey(
            firstPartySessionKeyPair.privateKey,
            firstPartySessionKeyPair.sessionKey.keyId,
            firstPartyEndpoint.nodeId,
            thirdPartyEndpoint.nodeId,
        )

        whenever(channelManager.getLinkedEndpointAddresses(firstPartyEndpoint))
            .thenReturn(setOf(thirdPartyEndpoint.nodeId))

        return EndpointChannel(
            firstPartyEndpoint,
            thirdPartyEndpoint,
            thirdPartySessionKeyPair,
            firstPartySessionKeyPair,
        )
    }

    protected suspend fun createFirstPartyEndpoint(
        firstPartyEndpoint: FirstPartyEndpoint = FirstPartyEndpointFactory.build(),
    ): FirstPartyEndpoint {
        val gatewayAddress = "example.org"
        privateKeyStore.saveIdentityKey(
            firstPartyEndpoint.identityPrivateKey,
        )

        val certificate = firstPartyEndpoint.identityCertificate
        certificateStore.save(
            CertificationPath(
                certificate,
                firstPartyEndpoint.identityCertificateChain,
            ),
            certificate.issuerCommonName,
        )

        if (MockUtil.isMock(storage)) {
            whenever(storage.gatewayId.get(firstPartyEndpoint.nodeId))
                .thenReturn(certificate.issuerCommonName)

            whenever(storage.internetAddress.get())
                .thenReturn(gatewayAddress)
        } else {
            storage.gatewayId.set(
                firstPartyEndpoint.nodeId,
                certificate.issuerCommonName,
            )

            storage.internetAddress.set(
                gatewayAddress,
            )
        }

        return firstPartyEndpoint
    }

    private suspend fun createThirdPartyEndpoint(
        thirdPartyEndpointType: RecipientAddressType,
        sessionKey: SessionKey,
        firstPartyEndpoint: FirstPartyEndpoint,
    ): ThirdPartyEndpoint {
        val thirdPartyEndpoint: ThirdPartyEndpoint
        when (thirdPartyEndpointType) {
            RecipientAddressType.PRIVATE -> {
                thirdPartyEndpoint = ThirdPartyEndpointFactory.buildPrivate()
                val authBundle =
                    CertificationPath(
                        PDACertPath.PDA,
                        listOf(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW),
                    )
                whenever(
                    storage.privateThirdParty.get(
                        "${firstPartyEndpoint.nodeId}_${thirdPartyEndpoint.nodeId}",
                    ),
                ).thenReturn(
                    PrivateThirdPartyEndpointData(
                        KeyPairSet.PDA_GRANTEE.public,
                        authBundle,
                        thirdPartyEndpoint.internetAddress,
                    ),
                )
            }
            else -> {
                thirdPartyEndpoint = ThirdPartyEndpointFactory.buildPublic()
                whenever(
                    storage.publicThirdParty.get(thirdPartyEndpoint.nodeId),
                ).thenReturn(
                    PublicThirdPartyEndpointData(
                        thirdPartyEndpoint.internetAddress,
                        thirdPartyEndpoint.identityKey,
                    ),
                )
            }
        }

        sessionPublicKeystore.save(
            sessionKey,
            firstPartyEndpoint.nodeId,
            thirdPartyEndpoint.nodeId,
        )
        return thirdPartyEndpoint
    }
}
