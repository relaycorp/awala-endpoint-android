package tech.relaycorp.awaladroid.endpoint

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runBlockingTest
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
    private val sharedPreferences = androidContext.getSharedPreferences(
        "channel-test",
        Context.MODE_PRIVATE
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

        assertEquals(Dispatchers.IO, manager.flowSharedPreferences.coroutineContext)
    }

    @Test
    fun create_non_existing() = runBlockingTest {
        assertEquals(
            null,
            sharedPreferences.getStringSet(firstPartyEndpoint.privateAddress, null)
        )
        val manager = ChannelManager(coroutineContext) { sharedPreferences }

        manager.create(firstPartyEndpoint, thirdPartyEndpoint)

        assertEquals(
            setOf(thirdPartyEndpoint.privateAddress),
            sharedPreferences.getStringSet(firstPartyEndpoint.privateAddress, null)
        )
    }

    @Test
    fun create_existing() = runBlockingTest {
        val manager = ChannelManager(coroutineContext) { sharedPreferences }
        manager.create(firstPartyEndpoint, thirdPartyEndpoint)

        manager.create(firstPartyEndpoint, thirdPartyEndpoint)

        assertEquals(
            setOf(thirdPartyEndpoint.privateAddress),
            sharedPreferences.getStringSet(firstPartyEndpoint.privateAddress, null)
        )
    }

    @Test
    fun create_with_thirdPartyEndpointPublicKey() = runBlockingTest {
        val manager = ChannelManager(coroutineContext) { sharedPreferences }
        manager.create(firstPartyEndpoint, thirdPartyEndpoint.identityKey)

        manager.create(firstPartyEndpoint, thirdPartyEndpoint)

        assertEquals(
            setOf(thirdPartyEndpoint.privateAddress),
            sharedPreferences.getStringSet(firstPartyEndpoint.privateAddress, null)
        )
    }

    @Test
    fun delete_first_party_non_existing() = runBlockingTest {
        val manager = ChannelManager(coroutineContext) { sharedPreferences }

        manager.delete(firstPartyEndpoint)

        assertEquals(
            null,
            sharedPreferences.getStringSet(firstPartyEndpoint.privateAddress, null)
        )
    }

    @Test
    fun delete_first_party_existing() = runBlockingTest {
        val manager = ChannelManager(coroutineContext) { sharedPreferences }
        manager.create(firstPartyEndpoint, thirdPartyEndpoint)

        manager.delete(firstPartyEndpoint)

        assertEquals(
            null,
            sharedPreferences.getStringSet(firstPartyEndpoint.privateAddress, null)
        )
    }

    @Test
    fun delete_third_party_non_existing() = runBlockingTest {
        val manager = ChannelManager(coroutineContext) { sharedPreferences }
        val unrelatedThirdPartyEndpointAddress = "i-have-nothing-to-do-with-the-other"
        with(sharedPreferences.edit()) {
            putStringSet(
                firstPartyEndpoint.privateAddress,
                mutableSetOf(unrelatedThirdPartyEndpointAddress)
            )
            apply()
        }

        manager.delete(thirdPartyEndpoint)

        assertEquals(
            mutableSetOf(unrelatedThirdPartyEndpointAddress),
            sharedPreferences.getStringSet(firstPartyEndpoint.privateAddress, null)
        )
    }

    @Test
    fun delete_third_party_existing() = runBlockingTest {
        val manager = ChannelManager(coroutineContext) { sharedPreferences }
        val unrelatedThirdPartyEndpointAddress = "i-have-nothing-to-do-with-the-other"
        with(sharedPreferences.edit()) {
            putStringSet(
                firstPartyEndpoint.privateAddress,
                mutableSetOf(unrelatedThirdPartyEndpointAddress, thirdPartyEndpoint.privateAddress)
            )
            apply()
        }

        manager.delete(thirdPartyEndpoint)

        assertEquals(
            setOf(unrelatedThirdPartyEndpointAddress),
            sharedPreferences.getStringSet(firstPartyEndpoint.privateAddress, null)
        )
    }

    @Test
    fun delete_third_party_single_valued() = runBlockingTest {
        val manager = ChannelManager(coroutineContext) { sharedPreferences }
        val malformedValue = "i-should-not-be-here"
        with(sharedPreferences.edit()) {
            putString(
                firstPartyEndpoint.privateAddress,
                malformedValue
            )
            apply()
        }

        manager.delete(thirdPartyEndpoint)

        assertEquals(
            malformedValue,
            sharedPreferences.getString(firstPartyEndpoint.privateAddress, null)
        )
    }

    @Test
    fun delete_third_party_invalid_type() = runBlockingTest {
        val manager = ChannelManager(coroutineContext) { sharedPreferences }
        val malformedValue = 42
        with(sharedPreferences.edit()) {
            putInt(
                firstPartyEndpoint.privateAddress,
                malformedValue
            )
            apply()
        }

        manager.delete(thirdPartyEndpoint)

        assertEquals(
            malformedValue,
            sharedPreferences.getInt(firstPartyEndpoint.privateAddress, 0)
        )
    }

    @Test
    fun getLinkedEndpointAddresses_empty() = runBlockingTest {
        val manager = ChannelManager(coroutineContext) { sharedPreferences }

        val linkedEndpoints = manager.getLinkedEndpointAddresses(firstPartyEndpoint)

        assertEquals(0, linkedEndpoints.size)
    }

    @Test
    fun getLinkedEndpointAddresses_matches() = runBlockingTest {
        val manager = ChannelManager(coroutineContext) { sharedPreferences }
        manager.create(firstPartyEndpoint, thirdPartyEndpoint)

        val linkedEndpoints = manager.getLinkedEndpointAddresses(firstPartyEndpoint)

        assertEquals(setOf(thirdPartyEndpoint.privateAddress), linkedEndpoints)
    }
}
