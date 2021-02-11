package tech.relaycorp.relaydroid.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import tech.relaycorp.relaydroid.messaging.MessageId

internal class MessageIdTest {
    @Test
    fun generate() {
        val messageId = MessageId.generate()
        assertNotNull(messageId.value)
    }

    @Test
    fun equals() {
        val messageId1 = MessageId.generate()
        val messageId2 = MessageId(messageId1.value)
        assertEquals(messageId1, messageId2)
    }
}
