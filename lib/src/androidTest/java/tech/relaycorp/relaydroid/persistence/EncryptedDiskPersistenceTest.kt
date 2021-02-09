package tech.relaycorp.relaydroid.persistence

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.Charset

@RunWith(AndroidJUnit4::class)
internal class EncryptedDiskPersistenceTest {

    private val coroutineScope = TestCoroutineScope()
    private val subject =
        EncryptedDiskPersistence(
            ApplicationProvider.getApplicationContext(),
            coroutineScope.coroutineContext
        )

    @After
    fun tearDown() {
        coroutineScope.runBlockingTest {
            subject.deleteAll()
        }
    }

    @Test
    fun getAndSet() = coroutineScope.runBlockingTest {
        assertNull(subject.get("file"))
        val data = "test"
        subject.set("file", data.toByteArray())
        assertEquals(data, subject.get("file")?.toString(Charset.defaultCharset()))
    }

    @Test
    fun delete() = coroutineScope.runBlockingTest {
        subject.set("file", "test".toByteArray())
        assertNotNull(subject.get("file"))
        subject.delete("file")
        assertNull(subject.get("file"))
    }

    @Test
    fun deleteAll() = coroutineScope.runBlockingTest {
        subject.set("file1", "test".toByteArray())
        subject.set("file2", "test".toByteArray())
        subject.deleteAll()
        assertNull(subject.get("file1"))
        assertNull(subject.get("file2"))
    }

    @Test
    fun list() = coroutineScope.runBlockingTest {
        subject.set("file1", "test".toByteArray())
        subject.set("file2", "test".toByteArray())
        subject.set("another", "test".toByteArray())

        with(subject.list()) {
            assertEquals(3, size)
            assertTrue(contains("file1"))
            assertTrue(contains("file2"))
            assertTrue(contains("another"))
        }

        with(subject.list("file")) {
            assertEquals(2, size)
            assertTrue(contains("file1"))
            assertTrue(contains("file2"))
            assertFalse(contains("another"))
        }
    }
}
