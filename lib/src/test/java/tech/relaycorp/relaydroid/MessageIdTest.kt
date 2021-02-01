package tech.relaycorp.relaydroid

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class MessageIdTest {
    @Test
    internal fun generate() {
        val messageId = MessageId.generate()
        assertNotNull(messageId.value)
    }

    @Test
    internal fun equals() {
        val messageId1 = MessageId.generate()
        val messageId2 = MessageId(messageId1.value)
        assertEquals(messageId1, messageId2)
    }
}
