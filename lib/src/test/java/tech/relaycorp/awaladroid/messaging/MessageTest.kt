package tech.relaycorp.awaladroid.messaging

import org.junit.Assert
import org.junit.Test
import tech.relaycorp.relaynet.ramf.RAMFMessage

public class MessageTest {
    @Test
    public fun maxContentSize() {
        val expectedMax = RAMFMessage.MAX_PAYLOAD_LENGTH - 1024

        Assert.assertEquals(Message.MAX_CONTENT_SIZE, expectedMax)
    }
}
