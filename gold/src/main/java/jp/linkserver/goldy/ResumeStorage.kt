package jp.linkserver.goldy

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import kotlinx.coroutines.*

/** All checkpoint readers, writers and explicit deletion share this cancellable OS lock. */
internal suspend fun <T> withResumeStorage(directory: File, block: suspend () -> T): T = withContext(Dispatchers.IO) {
    storageIo { if (!directory.exists() && !directory.mkdirs()) throw IOException("Transfer directory unavailable") }
    val file = storageIo { RandomAccessFile(File(directory, ".transfer.lock"), "rw") }
    file.use {
        var lock: FileLock? = null
        try {
            while (lock == null) {
                currentCoroutineContext().ensureActive()
                lock = try { storageIo { file.channel.tryLock() } } catch (_: OverlappingFileLockException) { null }
                if (lock == null) delay(50)
            }
            block()
        } finally { lock?.release() }
    }
}

internal inline fun <T> storageIo(block: () -> T): T = try { block() }
catch (error: IOException) { throw ApkException(ApkFailure.Storage, cause = error) }
