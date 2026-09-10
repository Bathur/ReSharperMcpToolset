// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

package local.bathur.resharper.mcp.toolset

import java.io.BufferedOutputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

internal data class FailureLogLimits(
    val fileBytes: Long = 8L * 1_024 * 1_024,
    val directoryBytes: Long = 128L * 1_024 * 1_024,
    val queueCapacity: Int = 32,
)

/** Best-effort single writer. No caller performs filesystem I/O or waits for a queue slot. */
internal class FailureLogWriter(
    private val directory: () -> Path,
    private val onFailure: (Throwable) -> Unit = {},
    private val limits: FailureLogLimits = FailureLogLimits(),
) : AutoCloseable {
    constructor(
        directory: Path,
        onFailure: (Throwable) -> Unit = {},
        limits: FailureLogLimits = FailureLogLimits(),
    ) : this({ directory }, onFailure, limits)

    private val queue = ArrayBlockingQueue<FailureLogSnapshot>(limits.queueCapacity)
    private val accepting = AtomicBoolean(true)
    private val started = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null

    /** A cheap hint before preparing a snapshot; admission can still race another caller. */
    fun canAccept(): Boolean = accepting.get() && queue.remainingCapacity() > 0

    fun tryOffer(snapshot: FailureLogSnapshot): Boolean {
        if (!accepting.get()) return false
        if (!queue.offer(snapshot)) return false
        if (!accepting.get()) {
            queue.clear()
            return false
        }
        if (started.compareAndSet(false, true)) {
            try {
                val thread = Thread(::runWriter, "ReSharper MCP failure log writer").apply {
                    isDaemon = true
                    // Do not retain the context loader of a tool-call worker.
                    contextClassLoader = FailureLogWriter::class.java.classLoader
                }
                worker = thread
                thread.start()
            } catch (_: Throwable) {
                accepting.set(false)
                queue.clear()
                return false
            }
        }
        return accepting.get()
    }

    override fun close() {
        accepting.set(false)
        queue.clear()
        worker?.interrupt()
        // Shutdown deliberately neither joins the worker nor drains pending records.
    }

    private fun runWriter() {
        var files: LogFiles? = null
        try {
            while (accepting.get()) {
                val first = queue.take()
                if (!accepting.get()) break
                val activeFiles = files ?: LogFiles(directory(), limits).also { files = it }
                activeFiles.write(first.encode())
                var batchSize = 1
                while (accepting.get() && batchSize < 8) {
                    val next = queue.poll() ?: break
                    activeFiles.write(next.encode())
                    batchSize++
                }
                activeFiles.flush()
            }
        } catch (_: InterruptedException) {
            // Application disposal wakes an idle writer; pending records may be lost.
        } catch (error: Throwable) {
            if (accepting.getAndSet(false)) {
                queue.clear()
                try {
                    onFailure(error)
                } catch (_: Throwable) {
                    // Even the optional native logger is outside the tool's failure path.
                }
            }
        } finally {
            accepting.set(false)
            queue.clear()
            try {
                files?.close()
            } catch (_: Throwable) {
                // No retry and no shutdown flush guarantee.
            }
        }
    }
}

private class LogFiles(private val directory: Path, private val limits: FailureLogLimits) : AutoCloseable {
    private val instance = UUID.randomUUID().toString()
    private var sequence = 0
    private var currentPath: Path? = null
    private var stream: BufferedOutputStream? = null
    private var currentBytes = 0L

    init {
        Files.createDirectories(directory)
    }

    fun write(bytes: ByteArray) {
        check(bytes.size <= FailureLogSnapshot.MaxRecordBytes)
        if (stream == null || currentBytes + bytes.size > limits.fileBytes) {
            closeCurrent()
            cleanup(bytes.size.toLong())
            openCurrent()
        }
        stream!!.write(bytes)
        currentBytes += bytes.size
    }

    fun flush() {
        stream?.flush()
    }

    private fun openCurrent() {
        val path = directory.resolve("cpp-mcp-failures-$instance-${sequence++}.jsonl")
        val channel = FileChannel.open(path, CREATE_NEW, WRITE)
        try {
            // Lock only the activity marker: Windows enforces byte-range locks on readers too.
            // Locking beyond EOF does not grow the file. Empty files are excluded from cleanup
            // until this lock is held and the first record is written.
            channel.lock(ActivityLockPosition, 1, false)
            stream = BufferedOutputStream(Channels.newOutputStream(channel), 64 * 1_024)
            currentPath = path
            currentBytes = 0
        } catch (error: Throwable) {
            channel.close()
            throw error
        }
    }

    private fun closeCurrent() {
        // Closing the stream also closes its channel and releases the exclusive file lock.
        val previous = stream
        stream = null
        currentPath = null
        currentBytes = 0
        previous?.close()
    }

    /** Only matching regular files are considered; active writers are protected by file locks. */
    private fun cleanup(incomingBytes: Long) {
        val candidates = mutableListOf<LogFile>()
        Files.newDirectoryStream(directory).use { entries ->
            for (path in entries) {
                if (!OwnedName.matches(path.fileName.toString())) continue
                val attributes = try {
                    Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                } catch (_: NoSuchFileException) {
                    continue // Another writer may have just cleaned this closed file.
                }
                if (attributes.isRegularFile) candidates += LogFile(path, attributes.size(), attributes.lastModifiedTime().toMillis())
            }
        }
        var total = candidates.sumOf { it.bytes } + incomingBytes
        if (total <= limits.directoryBytes) return
        for (candidate in candidates.sortedWith(compareBy<LogFile> { it.modified }.thenBy { it.path.toString() })) {
            if (total <= limits.directoryBytes) break
            if (candidate.path == currentPath || candidate.bytes == 0L) continue
            try {
                val closed = FileChannel.open(candidate.path, WRITE, NOFOLLOW_LINKS).use { channel ->
                    try {
                        val lock = channel.tryLock(ActivityLockPosition, 1, false) ?: return@use false
                        lock.release()
                        true
                    } catch (_: OverlappingFileLockException) {
                        false
                    }
                }
                // Each file has a unique name and is never reopened for writing. Once unlocked,
                // only concurrent cleanup can touch it, so closing our handle before deletion is safe.
                if (closed && Files.deleteIfExists(candidate.path)) total -= candidate.bytes
            } catch (_: NoSuchFileException) {
                total -= candidate.bytes
            }
        }
    }

    override fun close() = closeCurrent()

    private data class LogFile(val path: Path, val bytes: Long, val modified: Long)

    companion object {
        // Outside the JSONL data, but within the whole-file lock used by version 0.3.5.
        private const val ActivityLockPosition = Long.MAX_VALUE - 1
        private val OwnedName = Regex("cpp-mcp-failures-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-[0-9]+\\.jsonl")
    }
}
