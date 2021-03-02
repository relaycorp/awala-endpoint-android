package tech.relaycorp.relaydroid.storage.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.Charset

@RunWith(AndroidJUnit4::class)
internal class EncryptedDiskPersistenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val coroutineScope = TestCoroutineScope()
    private val rootFolder = "relaydroid_test"
    private val subject = EncryptedDiskPersistence(
        context,
        coroutineScope.coroutineContext,
        rootFolder
    )

    @After
    fun tearDown() {
        coroutineScope.runBlockingTest {
            subject.deleteAll()
        }
    }

    @Test
    fun getNonExistentFile() = coroutineScope.runBlockingTest {
        assertNull(subject.get("file"))
    }

    @Test
    fun setNonExistentFileAndGetIt() = coroutineScope.runBlockingTest {
        val data = "test"
        subject.set("file", data.toByteArray())
        assertEquals(data, subject.get("file")?.toString(Charset.defaultCharset()))
    }

    @Test
    fun setOnExistingFile() = coroutineScope.runBlockingTest {
        val data1 = "test1"
        val data2 = "test2"
        subject.set("file", data1.toByteArray())
        subject.set("file", data2.toByteArray())
        assertEquals(data2, subject.get("file")?.toString(Charset.defaultCharset()))
    }

    @Test
    fun setEncryptsContent() = coroutineScope.runBlockingTest {
        val location = "file"
        val data = "test"
        subject.set(location, data.toByteArray())
        val fileContent =
            File(context.filesDir, "$rootFolder${File.separator}$location")
                .readBytes()
                .toString(Charset.defaultCharset())
        assertNotEquals(data, fileContent)
    }

    @Test
    fun deleteExistingFile() = coroutineScope.runBlockingTest {
        subject.set("file", "test".toByteArray())
        assertNotNull(subject.get("file"))
        subject.delete("file")
        assertNull(subject.get("file"))
    }

    @Test(expected = PersistenceException::class)
    fun deleteNonExistentFile() = coroutineScope.runBlockingTest {
        assertNull(subject.get("file"))
        subject.delete("file")
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
    fun deleteAll_withPrefix() = coroutineScope.runBlockingTest {
        subject.set("file1", "test".toByteArray())
        subject.set("different2", "test".toByteArray())
        subject.deleteAll("file")
        assertNull(subject.get("file1"))
        assertNotNull(subject.get("different2"))
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
