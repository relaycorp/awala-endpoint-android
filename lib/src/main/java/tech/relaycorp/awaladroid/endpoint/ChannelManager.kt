package tech.relaycorp.awaladroid.endpoint

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.relaycorp.relaynet.wrappers.nodeId
import java.security.PublicKey
import kotlin.coroutines.CoroutineContext

internal class ChannelManager(
    internal val coroutineContext: CoroutineContext = Dispatchers.IO,
    sharedPreferencesGetter: () -> SharedPreferences,
) {
    internal val sharedPreferences by lazy(sharedPreferencesGetter)

    suspend fun create(
        firstPartyEndpoint: FirstPartyEndpoint,
        thirdPartyEndpoint: ThirdPartyEndpoint,
    ) {
        create(firstPartyEndpoint, thirdPartyEndpoint.nodeId)
    }

    suspend fun create(
        firstPartyEndpoint: FirstPartyEndpoint,
        thirdPartyEndpointPublicKey: PublicKey,
    ) {
        create(firstPartyEndpoint, thirdPartyEndpointPublicKey.nodeId)
    }

    private suspend fun create(
        firstPartyEndpoint: FirstPartyEndpoint,
        thirdPartyEndpointNodeId: String,
    ) {
        withContext(coroutineContext) {
            val originalValue =
                sharedPreferences.getStringSet(firstPartyEndpoint.nodeId, null)
                    ?: emptySet()
            with(sharedPreferences.edit()) {
                putStringSet(
                    firstPartyEndpoint.nodeId,
                    originalValue + mutableListOf(thirdPartyEndpointNodeId),
                )
                commit()
            }
        }
    }

    suspend fun delete(firstPartyEndpoint: FirstPartyEndpoint) {
        withContext(coroutineContext) {
            with(sharedPreferences.edit()) {
                remove(firstPartyEndpoint.nodeId)
                commit()
            }
        }
    }

    suspend fun delete(
        linkedFirstPartyEndpoint: FirstPartyEndpoint,
        thirdPartyEndpoint: ThirdPartyEndpoint,
    ) {
        withContext(coroutineContext) {
            sharedPreferences.all.forEach { (key, value) ->
                // Skip malformed values
                if (value !is MutableSet<*>) {
                    return@forEach
                }
                val sanitizedValue: List<String> = value.filterIsInstance<String>()
                if (value.size != sanitizedValue.size) {
                    return@forEach
                }

                if (key != linkedFirstPartyEndpoint.nodeId) {
                    return@forEach
                }

                if ((value).contains(thirdPartyEndpoint.nodeId)) {
                    val newValue = sanitizedValue.filter { it != thirdPartyEndpoint.nodeId }
                    with(sharedPreferences.edit()) {
                        putStringSet(key, newValue.toMutableSet())
                        commit()
                    }
                }
            }
        }
    }

    suspend fun getLinkedEndpointAddresses(firstPartyEndpoint: FirstPartyEndpoint): Set<String> =
        withContext(coroutineContext) {
            return@withContext sharedPreferences.getStringSet(
                firstPartyEndpoint.nodeId,
                emptySet(),
            ) ?: emptySet()
        }
}
