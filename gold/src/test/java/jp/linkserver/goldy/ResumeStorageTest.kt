package jp.linkserver.goldy

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ResumeStorageTest {
    @Test fun waitingForOsLockCanBeCancelled() = runBlocking {
        val dir = Files.createTempDirectory("goldy-lock").toFile()
        try {
            RandomAccessFile(File(dir, ".transfer.lock"), "rw").use { file ->
                file.channel.lock().use {
                    var entered = false
                    val job = launch(Dispatchers.IO) { withResumeStorage(dir) { entered = true } }
                    delay(150)
                    withTimeout(2000) { job.cancelAndJoin() }
                    assertFalse(entered)
                }
            }
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }

    @Test fun cleanupPreservesValidResumeAndVerifiedFilesButRemovesOrphans() = runBlocking {
        val dir = Files.createTempDirectory("goldy-clean").toFile()
        val identity = ResumeCheckpoint.Identity("owner", "app", 1, "2.0", 2, "app.apk",
            "https://github.com/owner/app/releases/download/2.0/app.apk", 30, null)
        try {
            withResumeStorage(dir) {
                val store = ResumeCheckpoint(dir, 100)
                val part = store.file(identity)
                part.writeBytes(ByteArray(15) { it.toByte() })
                store.save(identity, ResumeHttp.Validator("ETag", "\"one\""))
                val orphan = File(dir, "goldy-resume-${"a".repeat(64)}.part").apply { writeText("orphan") }
                val malformed = File(dir, "goldy-resume-${"b".repeat(64)}.meta").apply { writeText("broken") }
                val brokenPart = File(dir, "goldy-resume-${"b".repeat(64)}.part").apply { writeText("broken") }
                val temp = File(dir, part.name.replace(".part", ".meta.tmp")).apply { writeText("interrupted write") }
                val apk = File(dir, "goldy-verified.apk").apply { writeText("verified caller file") }
                val unrelated = File(dir, "host.part").apply { writeText("host") }
                store.clean()
                assertNotNull(store.load(identity)); assertTrue(part.exists())
                assertFalse(orphan.exists()); assertFalse(malformed.exists()); assertFalse(brokenPart.exists()); assertFalse(temp.exists())
                assertEquals("verified caller file", apk.readText()); assertEquals("host", unrelated.readText())
            }
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
}
