package tech.relaycorp.awaladroid.storage.persistence

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import kotlin.io.path.createTempDirectory

internal class DiskPersistenceTest {
    private val coroutineScope = TestScope()
    private val rootFolder = "relaydroid_test"

    private lateinit var filesDir: String
    private lateinit var subject: DiskPersistence

    @Before
    fun initDiskPersistence(): Unit = runBlocking {
        filesDir = createTempDirectory("rootDir").toString()
        subject = DiskPersistence(
            filesDir,
            coroutineScope.coroutineContext,
            rootFolder,
        )
    }

    @Test
    fun getNonExistingFile() = coroutineScope.runTest {
        assertNull(subject.get("file"))
    }

    @Test
    fun setNonExistingFileAndGetIt() = coroutineScope.runTest {
        val data = "test"
        subject.set("file", data.toByteArray())
        assertEquals(data, subject.get("file")?.toString(Charset.defaultCharset()))
    }

    @Test
    fun setOnExistingFile() = coroutineScope.runTest {
        val data1 = "test1"
        val data2 = "test2"
        subject.set("file", data1.toByteArray())
        subject.set("file", data2.toByteArray())
        assertEquals(data2, subject.get("file")?.toString(Charset.defaultCharset()))
    }

    @Test
    fun setContent() = coroutineScope.runTest {
        val location = "file"
        val data = "test"
        subject.set(location, data.toByteArray())
        val fileContent =
            File(filesDir, "$rootFolder${File.separator}$location")
                .readBytes()
                .toString(Charset.defaultCharset())
        assertEquals(data, fileContent)
    }

    @Test
    fun deleteExistingFile() = coroutineScope.runTest {
        subject.set("file", "test".toByteArray())
        assertNotNull(subject.get("file"))
        subject.delete("file")
        assertNull(subject.get("file"))
    }

    @Test
    fun deleteNonExistingFile() {
        assertThrows(PersistenceException::class.java) {
            coroutineScope.runTest {
                subject.delete("file")
            }
        }
    }

    @Test
    fun deleteAll() = coroutineScope.runTest {
        subject.set("file1", "test".toByteArray())
        subject.set("file2", "test".toByteArray())
        subject.deleteAll()
        assertNull(subject.get("file1"))
        assertNull(subject.get("file2"))
    }

    @Test
    fun deleteAll_withPrefix() = coroutineScope.runTest {
        subject.set("file1", "test".toByteArray())
        subject.set("different2", "test".toByteArray())
        subject.deleteAll("file")
        assertNull(subject.get("file1"))
        assertNotNull(subject.get("different2"))
    }

    @Test
    fun list() = coroutineScope.runTest {
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
