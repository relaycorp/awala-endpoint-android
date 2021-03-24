package tech.relaycorp.awaladroid

import kotlinx.coroutines.test.runBlockingTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
internal class AwalaTest {

    @Rule
    @JvmField
    val expectedException: ExpectedException = ExpectedException.none()

    @Test
    fun callGatewayWithoutSetup() {
        Awala.gatewayClientImpl = null
        expectedException.expect(SetupPendingException::class.java)
        GatewayClient.unbind()
    }

    @Test
    fun callGatewayWithSetup() = runBlockingTest {
        Awala.setup(RuntimeEnvironment.systemContext)
        GatewayClient.unbind()
    }
}
