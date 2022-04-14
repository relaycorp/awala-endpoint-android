package tech.relaycorp.awaladroid.endpoint

import android.content.SharedPreferences
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import java.security.PublicKey
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import tech.relaycorp.relaynet.wrappers.privateAddress

internal class ChannelManager(
    coroutineContext: CoroutineContext = Dispatchers.IO,
    sharedPreferencesGetter: () -> SharedPreferences
) {
    internal val flowSharedPreferences: FlowSharedPreferences by lazy {
        FlowSharedPreferences(sharedPreferencesGetter(), coroutineContext)
    }

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
        val preference =
            flowSharedPreferences.getNullableStringSet(firstPartyEndpoint.privateAddress, null)
        val originalValues = preference.get() ?: emptySet()
        preference.setAndCommit(originalValues + mutableListOf(thirdPartyEndpointPrivateAddress))
    }

    suspend fun delete(
        firstPartyEndpoint: FirstPartyEndpoint,
    ) {
        val preference =
            flowSharedPreferences.getNullableStringSet(firstPartyEndpoint.privateAddress, null)
        preference.deleteAndCommit()
    }

    suspend fun delete(
        thirdPartyEndpoint: ThirdPartyEndpoint
    ) {
        flowSharedPreferences.sharedPreferences.all.forEach { (key, value) ->
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
                flowSharedPreferences.getStringSet(key).setAndCommit(newValue.toMutableSet())
            }
        }
    }

    fun getLinkedEndpointAddresses(firstPartyEndpoint: FirstPartyEndpoint): Set<String> =
        flowSharedPreferences.getStringSet(firstPartyEndpoint.privateAddress, emptySet()).get()
}
