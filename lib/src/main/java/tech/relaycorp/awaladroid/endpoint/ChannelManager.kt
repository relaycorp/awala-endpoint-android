package tech.relaycorp.awaladroid.endpoint

import android.content.SharedPreferences
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import kotlin.coroutines.CoroutineContext

internal class ChannelManager(
    sharedPreferences: SharedPreferences,
    coroutineContext: CoroutineContext
) {
    private val flowSharedPreferences: FlowSharedPreferences =
        FlowSharedPreferences(sharedPreferences, coroutineContext)

    suspend fun create(
        firstPartyEndpoint: FirstPartyEndpoint,
        thirdPartyEndpoint: ThirdPartyEndpoint
    ) {
        val preference =
            flowSharedPreferences.getNullableStringSet(firstPartyEndpoint.privateAddress, null)
        val originalValues = preference.get() ?: emptySet()
        preference.setAndCommit(originalValues + mutableListOf(thirdPartyEndpoint.privateAddress))
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
