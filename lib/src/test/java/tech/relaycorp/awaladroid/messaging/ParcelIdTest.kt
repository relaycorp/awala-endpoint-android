package tech.relaycorp.awaladroid.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class ParcelIdTest {
    @Test
    fun generate() {
        val messageId = ParcelId.generate()
        assertNotNull(messageId.value)
    }

    @Test
    fun equals() {
        val messageId1 = ParcelId.generate()
        val messageId2 = ParcelId(messageId1.value)
        assertEquals(messageId1, messageId2)
    }
}
