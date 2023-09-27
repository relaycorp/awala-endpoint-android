package tech.relaycorp.awaladroid

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import tech.relaycorp.awala.keystores.file.FileCertificateStore
import tech.relaycorp.awala.keystores.file.FileKeystoreRoot
import tech.relaycorp.awala.keystores.file.FileSessionPublicKeystore
import tech.relaycorp.awaladroid.background.ServiceInteractor
import tech.relaycorp.awaladroid.endpoint.ChannelManager
import tech.relaycorp.awaladroid.endpoint.FirstPartyEndpoint
import tech.relaycorp.awaladroid.endpoint.HandleGatewayCertificateChange
import tech.relaycorp.awaladroid.endpoint.RenewExpiringCertificates
import tech.relaycorp.awaladroid.storage.StorageImpl
import tech.relaycorp.awaladroid.storage.persistence.DiskPersistence
import tech.relaycorp.relaynet.nodes.EndpointManager
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
        val fileCertificateStore = FileCertificateStore(keystoreRoot)

        contextDeferred.complete(
            AwalaContext(
                StorageImpl(DiskPersistence(context.filesDir.path.toString())),
                GatewayClientImpl(
                    serviceInteractorBuilder = { ServiceInteractor(context) },
                ),
                EndpointManager(androidPrivateKeyStore, fileSessionPublicKeystore),
                ChannelManager {
                    context.getSharedPreferences("awaladroid-channels", Context.MODE_PRIVATE)
                },
                androidPrivateKeyStore,
                fileSessionPublicKeystore,
                fileCertificateStore,
                HandleGatewayCertificateChange(androidPrivateKeyStore),
            ),
        )

        coroutineScope {
            launch {
                RenewExpiringCertificates(androidPrivateKeyStore, FirstPartyEndpoint::load)()
                fileCertificateStore.deleteExpired()
            }
        }
    }

    internal var contextDeferred: CompletableDeferred<AwalaContext> = CompletableDeferred()

    internal fun getContextOrThrow(): AwalaContext = try {
        contextDeferred.getCompleted()
    } catch (e: IllegalStateException) {
        throw SetupPendingException()
    }

    internal suspend fun awaitContextOrThrow(timeout: Duration = 3.seconds): AwalaContext = try {
        withTimeout(timeout) {
            contextDeferred.await()
        }
    } catch (e: TimeoutCancellationException) {
        throw SetupPendingException()
    }
}

/**
 * Private gateway client.
 */
public val GatewayClient: GatewayClientImpl
    get() = Awala.getContextOrThrow().gatewayClient

public class SetupPendingException : AwaladroidException("Awala.setUp() has not been called")
