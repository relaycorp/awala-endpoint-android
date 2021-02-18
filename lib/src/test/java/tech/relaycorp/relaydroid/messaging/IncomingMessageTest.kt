package tech.relaycorp.relaydroid.messaging

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tech.relaycorp.relaydroid.Relaynet
import tech.relaycorp.relaydroid.StorageImpl
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID

internal class IncomingMessageTest {

    private val storage = mock<StorageImpl>()

    @Before
    fun setUp() {
        runBlockingTest {
            Relaynet.storage = storage
            whenever(storage.getIdentityCertificate(any())).thenReturn(PDACertPath.PRIVATE_ENDPOINT)
            whenever(storage.getIdentityKeyPair(any())).thenReturn(KeyPairSet.PRIVATE_ENDPOINT)
            whenever(storage.getGatewayCertificate()).thenReturn(PDACertPath.PRIVATE_GW)
        }
    }

    @Test
    fun buildFromParcel() = runBlockingTest {
        val parcel = Parcel(
            recipientAddress = UUID.randomUUID().toString(),
            payload = "1234".toByteArray(),
            senderCertificate = PDACertPath.PRIVATE_ENDPOINT
        )

        val message = IncomingMessage.build(parcel) {}

        verify(storage).getIdentityCertificate(eq(parcel.recipientAddress))

        assertEquals(PDACertPath.PRIVATE_ENDPOINT, message.recipientEndpoint.certificate)
        assertArrayEquals(parcel.payload, message.payload)
        assertEquals(parcel.id, message.id.value)
        assertSameDateTime(parcel.creationDate, message.creationDate)
        assertSameDateTime(parcel.expiryDate, message.expirationDate)
    }

    private fun assertSameDateTime(date1: ZonedDateTime, date2: ZonedDateTime) =
        assertTrue(Duration.between(date1, date2).seconds < 2)
}
