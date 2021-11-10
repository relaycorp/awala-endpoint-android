package tech.relaycorp.awaladroid

import android.content.Context
import java.io.File
import tech.relaycorp.awala.keystores.file.FileKeystoreRoot
import tech.relaycorp.awala.keystores.file.FileSessionPublicKeystore
import tech.relaycorp.awaladroid.background.ServiceInteractor
import tech.relaycorp.awaladroid.storage.StorageImpl
import tech.relaycorp.awaladroid.storage.persistence.EncryptedDiskPersistence
import tech.relaycorp.relaynet.nodes.EndpointManager

public object Awala {
    internal const val POWEB_PORT = 13276
    internal const val GATEWAY_PACKAGE =
        "tech.relaycorp.gateway"
    internal const val GATEWAY_PRE_REGISTER_ACTION =
        "tech.relaycorp.gateway.ENDPOINT_PRE_REGISTRATION"
    internal const val GATEWAY_PRE_REGISTER_COMPONENT =
        "tech.relaycorp.gateway.background.endpoint.EndpointPreRegistrationService"
    internal const val GATEWAY_SYNC_COMPONENT =
        "tech.relaycorp.gateway.background.endpoint.GatewaySyncService"
    internal const val GATEWAY_SYNC_ACTION =
        "tech.relaycorp.gateway.SYNC"

    /**
     * Set up the endpoint library.
     */
    public suspend fun setUp(context: Context) {
        val keystoreRoot =
            FileKeystoreRoot(File(context.filesDir, "awaladroid${File.separator}keystores"))
        val androidPrivateKeyStore = AndroidPrivateKeyStore(keystoreRoot, context)
        val fileSessionPublicKeystore = FileSessionPublicKeystore(keystoreRoot)
        this.context = AwalaContext(
            StorageImpl(EncryptedDiskPersistence(context)),
            GatewayClientImpl(
                serviceInteractorBuilder = { ServiceInteractor(context) }
            ),
            EndpointManager(androidPrivateKeyStore, fileSessionPublicKeystore),
            androidPrivateKeyStore,
            fileSessionPublicKeystore,
        )

        storage = this.context!!.storage
        gatewayClientImpl = this.context!!.gatewayClient
    }

    internal fun tearDown() {
        context = null
    }

    internal lateinit var storage: StorageImpl
    internal var gatewayClientImpl: GatewayClientImpl? = null

    private var context: AwalaContext? = null
    internal fun getContext(): AwalaContext = context ?: throw SetupPendingException()
}

/**
 * Private gateway client.
 */
public val GatewayClient: GatewayClientImpl
    get() = Awala.gatewayClientImpl ?: throw SetupPendingException()

internal val Storage get() = Awala.storage

public class SetupPendingException :
    AwaladroidException("Call Awala.setup before using the GatewayClient")
