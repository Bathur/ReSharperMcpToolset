// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

package local.bathur.resharper.mcp.toolset

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.FileTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--active-log-probe") {
        activeLogProbe(Path.of(args[1]), Path.of(args[2]), args[3].toInt())
        return
    }
    val root = Files.createDirectories(Path.of(args.single())).resolve(UUID.randomUUID().toString())
    Files.createDirectories(root)
    snapshotTests()
    queueTests(root.resolve("queue"))
    diskFailureTests(root.resolve("disk-failure"))
    workerErrorTests()
    rotationTests(root.resolve("rotation"))
    cleanupTests(root.resolve("cleanup"))
    crossProcessTests(root.resolve("cross-process"))
    runToolObservationTests()
    println("Failure log and tool observation checks passed. Evidence: $root")
}

private fun sample(arguments: Map<String, Any?> = mapOf("file_path" to "C:/Project/A.cpp", "line" to 4)) =
    FailureLogSnapshot.capture(
        tool = "resharper_cpp_inspect_symbol", arguments = arguments, outcome = "response",
        durationMillis = 12, pluginVersion = "test", riderBuild = "RD-test",
        projectName = "Test", projectBasePath = "C:/Project",
        response = JsonObject(mapOf("status" to JsonPrimitive("not_found"))),
    )

private fun snapshotTests() {
    val arguments = mutableMapOf<String, Any?>("file_path" to "C:/工作/😀.cpp", "line" to 12)
    val diagnostics = mutableListOf(JsonObject(mapOf("code" to JsonPrimitive("missing"), "message" to JsonPrimitive("missing target"))))
    val snapshot = FailureLogSnapshot.capture(
        tool = "test", arguments = arguments, outcome = "response", durationMillis = 10,
        pluginVersion = "test", riderBuild = "RD-test",
        response = JsonObject(mapOf(
            "status" to JsonPrimitive("partial"),
            "diagnostics" to JsonArray(diagnostics),
            "references" to JsonArray(List(1_000) { JsonPrimitive("must not be copied") }),
        )),
    )
    arguments["line"] = 99
    diagnostics.clear()
    val row = decode(snapshot.encode())
    check(row["arguments"]!!.jsonObject["line"]!!.jsonPrimitive.int == 12)
    check((row["response"]!!.jsonObject["diagnostics"] as JsonArray).size == 1)
    check("references" !in row["response"]!!.jsonObject)
    check(row["result_collection_counts"]!!.jsonObject["references"]!!.jsonPrimitive.int == 1_000)
    check(!row["truncation"]!!.jsonObject["applied"]!!.jsonPrimitive.boolean)

    val hostile = "\u0000\"\\\n😀中".repeat(100_000)
    val huge = sample(mapOf("file_path" to "C:/重要/😀.cpp", "payload" to hostile, "kinds" to List(10_000) { hostile }, "line" to 17, "column" to 23))
    val encoded = huge.encode()
    val hugeRow = decode(encoded)
    check(encoded.size <= 256 * 1_024)
    check(hugeRow["truncation"]!!.jsonObject["applied"]!!.jsonPrimitive.boolean)
    check(hugeRow["tool"]!!.jsonPrimitive.content == "resharper_cpp_inspect_symbol")
    check(hugeRow["status"]!!.jsonPrimitive.content == "not_found")
    check(hugeRow["arguments"]!!.jsonObject["file_path"]!!.jsonPrimitive.content == "C:/重要/😀.cpp")
    check(hugeRow["arguments"]!!.jsonObject["line"]!!.jsonPrimitive.int == 17)
    check(hugeRow["arguments"]!!.jsonObject["column"]!!.jsonPrimitive.int == 23)
    check(encoded.count { it == '\n'.code.toByte() } == 1)

    val failure = IllegalStateException("operation failed", IllegalArgumentException("invalid nested state"))
    failure.stackTrace = Array(500) { StackTraceElement("Type", "method", "Source.kt", it + 1) }
    val failed = FailureLogSnapshot.capture(
        tool = "test", arguments = emptyMap(), outcome = "error", durationMillis = 1,
        pluginVersion = "test", riderBuild = "RD-test", failure = failure,
    )
    failure.stackTrace = emptyArray()
    val failedRow = decode(failed.encode())
    val causes = failedRow["failure"] as JsonArray
    check(causes.size == 2)
    check((causes[0].jsonObject["stack"] as JsonArray).size == 64)
    check(failedRow["truncation"]!!.jsonObject["applied"]!!.jsonPrimitive.boolean)
}

private fun queueTests(directory: Path) {
    val directoryEntered = CountDownLatch(1)
    val releaseDirectory = CountDownLatch(1)
    val directoryCalls = AtomicInteger()
    val caller = Thread.currentThread()
    val writer = FailureLogWriter(directory = {
        check(Thread.currentThread() != caller)
        directoryCalls.incrementAndGet()
        directoryEntered.countDown()
        check(releaseDirectory.await(5, TimeUnit.SECONDS))
        directory
    })
    check(!Files.exists(directory))
    check(writer.canAccept())
    check(directoryCalls.get() == 0)
    check(writer.tryOffer(sample()))
    check(directoryEntered.await(5, TimeUnit.SECONDS))
    repeat(32) { check(writer.tryOffer(sample())) }
    check(!writer.canAccept())
    val started = System.nanoTime()
    check(!writer.tryOffer(sample()))
    check(!writer.canAccept())
    check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1_000)
    val closeStarted = System.nanoTime()
    writer.close()
    check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - closeStarted) < 1_000)
    check(!writer.tryOffer(sample()))
    releaseDirectory.countDown()
    check(directoryCalls.get() == 1)
}

private fun diskFailureTests(directory: Path) {
    Files.createDirectories(directory.parent)
    Files.writeString(directory, "A regular file deliberately blocks directory creation")
    val failures = AtomicInteger()
    val reported = CountDownLatch(1)
    val writer = FailureLogWriter(directory, onFailure = {
        failures.incrementAndGet()
        reported.countDown()
        throw IllegalStateException("Even the reporting callback is allowed to fail")
    })
    // Startup may fail before the initial offer finishes; either return value is valid.
    writer.tryOffer(sample())
    check(reported.await(5, TimeUnit.SECONDS))
    eventually { !writer.tryOffer(sample()) }
    check(failures.get() == 1)
    writer.close()
}

private fun rotationTests(directory: Path) {
    val record = sample(mapOf("file_path" to "C:/Project/A.cpp", "payload" to "x".repeat(1_024)))
    val rowSize = record.encode().size
    val limit = rowSize * 2L
    val writer = FailureLogWriter(directory, limits = FailureLogLimits(fileBytes = limit, directoryBytes = limit * 10))
    repeat(5) { check(writer.tryOffer(record)) }
    eventually { logFiles(directory).sumOf { Files.size(it) } == rowSize * 5L }
    writer.close()
    eventually { logFiles(directory).all(::canLock) }
    val files = logFiles(directory)
    check(files.size == 3)
    check(files.all { Files.size(it) <= limit })
    check(files.sumOf { Files.readAllLines(it).size } == 5)
    files.forEach { file -> Files.readAllLines(file).forEach { Json.parseToJsonElement(it) } }
}

private fun workerErrorTests() {
    val expected = AssertionError("A logger-internal error must not reach the IDE uncaught handler")
    val reported = CountDownLatch(1)
    val writer = FailureLogWriter(
        directory = { throw expected },
        onFailure = {
            check(it === expected)
            reported.countDown()
        },
    )
    // Startup may fail before the initial offer finishes; either return value is valid.
    writer.tryOffer(sample())
    check(reported.await(5, TimeUnit.SECONDS))
    eventually { !writer.canAccept() }
    check(!writer.tryOffer(sample()))
    writer.close()
}

private fun cleanupTests(directory: Path) {
    Files.createDirectories(directory)
    val unrelated = directory.resolve("idea.log")
    Files.writeString(unrelated, "unrelated log must stay")
    val old = directory.resolve("cpp-mcp-failures-${UUID.randomUUID()}-0.jsonl")
    Files.writeString(old, "x".repeat(4_096))
    Files.setLastModifiedTime(old, FileTime.fromMillis(1))
    val active = directory.resolve("cpp-mcp-failures-${UUID.randomUUID()}-0.jsonl")
    Files.writeString(active, "x".repeat(4_096))
    Files.setLastModifiedTime(active, FileTime.fromMillis(2))
    val empty = directory.resolve("cpp-mcp-failures-${UUID.randomUUID()}-0.jsonl")
    Files.createFile(empty)
    FileChannel.open(active, WRITE).use { channel ->
        channel.lock().use {
            val writer = FailureLogWriter(directory, limits = FailureLogLimits(directoryBytes = 2_048))
            check(writer.tryOffer(sample()))
            eventually { !Files.exists(old) }
            eventually { logFiles(directory).any { it != active && it != empty && Files.size(it) > 0 } }
            check(Files.exists(active))
            check(Files.exists(empty))
            check(Files.readString(unrelated) == "unrelated log must stay")
            writer.close()
        }
    }
}

/** Windows byte-range locks affect other processes even when same-JVM reads succeed. */
private fun crossProcessTests(root: Path) {
    val directory = root.resolve("logs")
    val record = sample()
    val rowSize = record.encode().size
    FailureLogWriter(directory).use { writer ->
        check(writer.tryOffer(record))
        eventually { logFiles(directory).sumOf { Files.size(it) } == rowSize.toLong() }
        val active = logFiles(directory).single()
        check(!canLock(active))
        runActiveLogProbe(root, directory, active, expectedRows = 1)
        check(Files.exists(active))
        check(writer.tryOffer(record))
        eventually { Files.size(active) == rowSize * 2L }
        runActiveLogProbe(root, directory, active, expectedRows = 2)
    }
    eventually { logFiles(directory).all(::canLock) }
}

private fun runActiveLogProbe(root: Path, directory: Path, active: Path, expectedRows: Int) {
    val arguments = listOf(
        "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
        "-cp", System.getProperty("java.class.path"),
        "local.bathur.resharper.mcp.toolset.FailureLogTestsKt", "--active-log-probe",
        directory.toString(), active.toString(), expectedRows.toString(),
    )
    // The Rider test classpath can exceed the Windows command-line limit.
    val argumentFile = root.resolve("probe-$expectedRows.args")
    Files.writeString(argumentFile, arguments.joinToString("\n") {
        "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    })
    val javaName = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
    val java = Path.of(System.getProperty("java.home"), "bin", javaName)
    val output = root.resolve("probe-$expectedRows-output.txt")
    val process = ProcessBuilder(java.toString(), "@${argumentFile.toAbsolutePath()}")
        .redirectErrorStream(true).redirectOutput(output.toFile()).start()
    if (!process.waitFor(20, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        error("Active-log probe did not finish: $output")
    }
    check(process.exitValue() == 0) { "Active-log probe failed:\n${Files.readString(output)}" }
    check(Files.readString(output).contains("Active log read and cleanup checks passed"))
}

private fun activeLogProbe(directory: Path, active: Path, expectedRows: Int) {
    // A fresh JVM may initialize its entropy source slowly; do so before timing writer I/O.
    UUID.randomUUID()
    val rows = Files.readAllLines(active)
    check(rows.size == expectedRows)
    rows.forEach { check(Json.parseToJsonElement(it).jsonObject["status"]!!.jsonPrimitive.content == "not_found") }
    check(!canLock(active)) { "The other process's active log must remain locked against cleanup" }
    val beforeCleanup = logFiles(directory).toSet()
    val cleanupFailure = AtomicReference<Throwable>()
    FailureLogWriter(directory, onFailure = cleanupFailure::set, limits = FailureLogLimits(directoryBytes = 1)).use { cleaner ->
        check(cleaner.tryOffer(sample()))
        eventually {
            cleanupFailure.get()?.let { throw AssertionError("Cross-process cleanup failed", it) }
            logFiles(directory).any { it !in beforeCleanup && Files.size(it) > 0 }
        }
        check(Files.exists(active)) { "Cleanup removed the other process's active log" }
        check(Files.readAllLines(active).size == expectedRows)
    }
    eventually { logFiles(directory).filter { it != active }.all(::canLock) }
    println("Active log read and cleanup checks passed in process ${ProcessHandle.current().pid()}")
}

private fun decode(bytes: ByteArray): JsonObject {
    check(bytes.last() == '\n'.code.toByte())
    return Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
}

private fun logFiles(directory: Path): List<Path> =
    if (!Files.isDirectory(directory)) emptyList() else Files.newDirectoryStream(directory, "cpp-mcp-failures-*.jsonl").use { it.toList() }

private fun canLock(path: Path): Boolean = try {
    FileChannel.open(path, WRITE).use { channel -> channel.tryLock()?.use { true } ?: false }
} catch (_: java.nio.channels.OverlappingFileLockException) {
    false
}

private fun eventually(condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (!condition()) {
        check(System.nanoTime() < deadline) { "Timed out waiting for the background writer" }
        Thread.sleep(10)
    }
}
