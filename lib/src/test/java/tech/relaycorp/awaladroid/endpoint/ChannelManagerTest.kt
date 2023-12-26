package tech.relaycorp.awaladroid.endpoint

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import tech.relaycorp.awaladroid.test.FirstPartyEndpointFactory
import tech.relaycorp.awaladroid.test.ThirdPartyEndpointFactory

@RunWith(RobolectricTestRunner::class)
internal class ChannelManagerTest {
    private val androidContext = RuntimeEnvironment.getApplication()
    private val sharedPreferences =
        androidContext.getSharedPreferences(
            "channel-test",
            Context.MODE_PRIVATE,
        )

    private val firstPartyEndpoint = FirstPartyEndpointFactory.build()
    private val thirdPartyEndpoint = ThirdPartyEndpointFactory.buildPrivate()

    @After
    fun clearPreferences() {
        with(sharedPreferences.edit()) {
            clear()
            apply()
        }
    }

    @Test
    fun constructor_defaultCoroutineContext() {
        val manager = ChannelManager { sharedPreferences }

        assertEquals(Dispatchers.IO, manager.coroutineContext)
    }

    @Test
    fun create_non_existing() =
        runTest {
            assertEquals(
                null,
                sharedPreferences.getStringSet(firstPartyEndpoint.nodeId, null),
            )
            val manager = ChannelManager(coroutineContext) { sharedPreferences }

            manager.create(firstPartyEndpoint, thirdPartyEndpoint)

            assertEquals(
                setOf(thirdPartyEndpoint.nodeId),
                sharedPreferences.getStringSet(firstPartyEndpoint.nodeId, null),
            )
        }

    @Test
    fun create_existing() =
        runTest {
            val manager = ChannelManager(coroutineContext) { sharedPreferences }
            manager.create(firstPartyEndpoint, thirdPartyEndpoint)

            manager.create(firstPartyEndpoint, thirdPartyEndpoint)

            assertEquals(
                setOf(thirdPartyEndpoint.nodeId),
                sharedPreferences.getStringSet(firstPartyEndpoint.nodeId, null),
            )
        }

    @Test
    fun create_with_thirdPartyEndpointPublicKey() =
        runTest {
            val manager = ChannelManager(coroutineContext) { sharedPreferences }
            manager.create(firstPartyEndpoint, thirdPartyEndpoint.identityKey)

            manager.create(firstPartyEndpoint, thirdPartyEndpoint)

            assertEquals(
                setOf(thirdPartyEndpoint.nodeId),
                sharedPreferences.getStringSet(firstPartyEndpoint.nodeId, null),
            )
        }

    @Test
    fun delete_first_party_non_existing() =
        runTest {
            val manager = ChannelManager(coroutineContext) { sharedPreferences }

            manager.delete(firstPartyEndpoint)

            assertEquals(
                null,
                sharedPreferences.getStringSet(firstPartyEndpoint.nodeId, null),
            )
        }

    @Test
    fun delete_first_party_existing() =
        runTest {
            val manager = ChannelManager(coroutineContext) { sharedPreferences }
            manager.create(firstPartyEndpoint, thirdPartyEndpoint)

            manager.delete(firstPartyEndpoint)

            assertEquals(
                null,
                sharedPreferences.getStringSet(firstPartyEndpoint.nodeId, null),
            )
        }

    @Test
    fun delete_third_party_non_existing() =
        runTest {
            val manager = ChannelManager(coroutineContext) { sharedPreferences }
            val unrelatedThirdPartyEndpointAddress = "i-have-nothing-to-do-with-the-other"
            with(sharedPreferences.edit()) {
                putStringSet(
                    firstPartyEndpoint.nodeId,
                    mutableSetOf(unrelatedThirdPartyEndpointAddress),
                )
                apply()
            }

            manager.delete(firstPartyEndpoint, thirdPartyEndpoint)

            assertEquals(
                mutableSetOf(unrelatedThirdPartyEndpointAddress),
                sharedPreferences.getStringSet(firstPartyEndpoint.nodeId, null),
            )
        }

    @Test
    fun delete_third_party_existing() =
        runTest {
            val manager = ChannelManager(coroutineContext) { sharedPreferences }
            val unrelatedThirdPartyEndpointAddress = "i-have-nothing-to-do-with-the-other"
            with(sharedPreferences.edit()) {
                putStringSet(
                    firstPartyEndpoint.nodeId,
                    mutableSetOf(unrelatedThirdPartyEndpointAddress, thirdPartyEndpoint.nodeId),
                )
                apply()
            }

            manager.delete(firstPartyEndpoint, thirdPartyEndpoint)

            assertEquals(
                setOf(unrelatedThirdPartyEndpointAddress),
                sharedPreferences.getStringSet(firstPartyEndpoint.nodeId, null),
            )
        }

    @Test
    fun delete_third_party_single_valued() =
        runTest {
            val manager = ChannelManager(coroutineContext) { sharedPreferences }
            val malformedValue = "i-should-not-be-here"
            with(sharedPreferences.edit()) {
                putString(
                    firstPartyEndpoint.nodeId,
                    malformedValue,
                )
                apply()
            }

            manager.delete(firstPartyEndpoint, thirdPartyEndpoint)

            assertEquals(
                malformedValue,
                sharedPreferences.getString(firstPartyEndpoint.nodeId, null),
            )
        }

    @Test
    fun delete_third_party_invalid_type() =
        runTest {
            val manager = ChannelManager(coroutineContext) { sharedPreferences }
            val malformedValue = 42
            with(sharedPreferences.edit()) {
                putInt(
                    firstPartyEndpoint.nodeId,
                    malformedValue,
                )
                apply()
            }

            manager.delete(firstPartyEndpoint, thirdPartyEndpoint)

            assertEquals(
                malformedValue,
                sharedPreferences.getInt(firstPartyEndpoint.nodeId, 0),
            )
        }

    @Test
    fun getLinkedEndpointAddresses_empty() =
        runTest {
            val manager = ChannelManager(coroutineContext) { sharedPreferences }

            val linkedEndpoints = manager.getLinkedEndpointAddresses(firstPartyEndpoint)

            assertEquals(0, linkedEndpoints.size)
        }

    @Test
    fun getLinkedEndpointAddresses_matches() =
        runTest {
            val manager = ChannelManager(coroutineContext) { sharedPreferences }
            manager.create(firstPartyEndpoint, thirdPartyEndpoint)

            val linkedEndpoints = manager.getLinkedEndpointAddresses(firstPartyEndpoint)

            assertEquals(setOf(thirdPartyEndpoint.nodeId), linkedEndpoints)
        }
}
