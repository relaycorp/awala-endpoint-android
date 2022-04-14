package tech.relaycorp.awaladroid.endpoint

import android.content.SharedPreferences
import java.security.PublicKey
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.relaycorp.relaynet.wrappers.privateAddress

internal class ChannelManager(
    internal val coroutineContext: CoroutineContext = Dispatchers.IO,
    sharedPreferencesGetter: () -> SharedPreferences
) {
    internal val sharedPreferences by lazy(sharedPreferencesGetter)

    suspend fun create(
        firstPartyEndpoint: FirstPartyEndpoint,
        thirdPartyEndpoint: ThirdPartyEndpoint
    ) {
        create(firstPartyEndpoint, thirdPartyEndpoint.privateAddress)
    }

    suspend fun create(
        firstPartyEndpoint: FirstPartyEndpoint,
        thirdPartyEndpointPublicKey: PublicKey
    ) {
        create(firstPartyEndpoint, thirdPartyEndpointPublicKey.privateAddress)
    }

    private suspend fun create(
        firstPartyEndpoint: FirstPartyEndpoint,
        thirdPartyEndpointPrivateAddress: String
    ) {
        withContext(coroutineContext) {
            val originalValue =
                sharedPreferences.getStringSet(firstPartyEndpoint.privateAddress, null)
                    ?: emptySet()
            with(sharedPreferences.edit()) {
                putStringSet(
                    firstPartyEndpoint.privateAddress,
                    originalValue + mutableListOf(thirdPartyEndpointPrivateAddress)
                )
                commit()
            }
        }
    }

    suspend fun delete(
        firstPartyEndpoint: FirstPartyEndpoint,
    ) {
        withContext(coroutineContext) {
            with(sharedPreferences.edit()) {
                remove(firstPartyEndpoint.privateAddress)
                commit()
            }
        }
    }

    suspend fun delete(
        thirdPartyEndpoint: ThirdPartyEndpoint
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

                if ((value).contains(thirdPartyEndpoint.privateAddress)) {
                    val newValue = sanitizedValue.filter { it != thirdPartyEndpoint.privateAddress }
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
                firstPartyEndpoint.privateAddress,
                emptySet()
            ) ?: emptySet()
        }
}
