// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

package local.bathur.resharper.mcp.toolset

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val root = Path.of(args.single()).toAbsolutePath().normalize().resolve(UUID.randomUUID().toString())
    Files.createDirectories(root)
    registrationPathTests(root)
    runBlocking { registrationFailureTests() }
    println("Registration path and failure recovery checks passed. Evidence: $root")
}

private fun registrationPathTests(root: Path) {
    val parent = Files.createDirectory(root.resolve("parent"))
    val child = Files.createDirectory(parent.resolve("child"))
    val file = Files.createFile(child.resolve("Probe.h"))
    val outside = Files.createDirectory(root.resolve("outside"))
    val outsideFile = Files.createFile(outside.resolve("Outside.h"))

    val ordinary = ExistingCppRegistrationPaths.resolve(parent, file)
    check(ordinary.isStrictDescendant)
    check(ordinary.parentDirectory == parent.toRealPath())
    check(ordinary.filePath == file.toRealPath())
    check(!ExistingCppRegistrationPaths.resolve(parent, parent).isStrictDescendant)
    check(!ExistingCppRegistrationPaths.resolve(parent, outsideFile).isStrictDescendant)

    withDirectoryAlias(parent.resolve("escape"), outside) { alias ->
        val escapedFile = alias.resolve("Outside.h")
        check(Files.isRegularFile(escapedFile, NOFOLLOW_LINKS))
        // This is the input that the previous NOFOLLOW_LINKS prefix check accepted.
        check(escapedFile.toRealPath(NOFOLLOW_LINKS).startsWith(parent.toRealPath(NOFOLLOW_LINKS)))
        val resolved = ExistingCppRegistrationPaths.resolve(parent, escapedFile)
        check(!resolved.isStrictDescendant)
        check(resolved.filePath == outsideFile.toRealPath())
    }
    withDirectoryAlias(parent.resolve("inside"), child) { alias ->
        val resolved = ExistingCppRegistrationPaths.resolve(parent, alias.resolve("Probe.h"))
        check(resolved.isStrictDescendant)
        check(resolved.filePath == file.toRealPath())
    }
    withDirectoryAlias(root.resolve("parent-alias"), parent) { alias ->
        // Physical identity is consistent even when the parent itself is an alias.
        // The registration preflight's ordinary-directory restriction is unchanged.
        val parentAlias = ExistingCppRegistrationPaths.resolve(alias, alias.resolve("child/Probe.h"))
        check(parentAlias.isStrictDescendant)
        check(parentAlias.parentDirectory == parent.toRealPath())
        check(parentAlias.filePath == file.toRealPath())
        // The alias is an ancestor; the selected parent itself is an ordinary directory.
        val resolved = ExistingCppRegistrationPaths.resolve(alias.resolve("child"), alias.resolve("child/Probe.h"))
        check(resolved.isStrictDescendant)
        check(resolved.parentDirectory == child.toRealPath())
        check(resolved.filePath == file.toRealPath())
    }
    check(Files.isRegularFile(file))
    check(Files.isRegularFile(outsideFile))
}

private fun withDirectoryAlias(link: Path, target: Path, block: (Path) -> Unit) {
    check(!Files.exists(link, NOFOLLOW_LINKS))
    try {
        if (System.getProperty("os.name").startsWith("Windows")) {
            val builder = ProcessBuilder(
                "pwsh", "-NoProfile", "-NonInteractive", "-Command",
                "\$ErrorActionPreference = 'Stop'; New-Item -ItemType Junction " +
                    "-Path \$env:REGISTRATION_TEST_LINK -Target \$env:REGISTRATION_TEST_TARGET | Out-Null",
            ).redirectErrorStream(true)
            builder.environment()["REGISTRATION_TEST_LINK"] = link.toString()
            builder.environment()["REGISTRATION_TEST_TARGET"] = target.toString()
            val process = builder.start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                error("Timed out creating the registration test junction: $link")
            }
            check(process.exitValue() == 0) { process.inputStream.bufferedReader().readText() }
        } else {
            Files.createSymbolicLink(link, target)
        }
        block(link)
    } finally {
        // Exact link deletion does not recurse into its target.
        Files.deleteIfExists(link)
    }
}

private suspend fun registrationFailureTests() {
    val failureFactory: (String, Exception) -> Exception = { message, cause ->
        IllegalStateException(message, cause)
    }
    val value = Any()
    check(withRegistrationFailureContext(failureFactory) { value } === value)

    for (stage in RegistrationProgress.Stage.entries) {
        val original = IllegalArgumentException("Injected RD failure")
        val failure = runCatching {
            withRegistrationFailureContext(failureFactory) { progress ->
                progress.stage = stage
                throw original
            }
        }.exceptionOrNull()!!
        check(failure.cause === original)
        check(failure.message!!.startsWith("Injected RD failure "))
        when (stage) {
            RegistrationProgress.Stage.NotAttempted -> {
                check("No project model modification was attempted" in failure.message!!)
                check(SameRegistrationRetry !in failure.message!!)
            }
            RegistrationProgress.Stage.CommitUnknown -> {
                check("commit result is unknown" in failure.message!!)
                check(SameRegistrationRetry in failure.message!!)
            }
            RegistrationProgress.Stage.Accepted -> {
                check("Rider accepted registration" in failure.message!!)
                check("final verification result is unavailable" in failure.message!!)
                check(SameRegistrationRetry in failure.message!!)
                check("commit result is unknown" !in failure.message!!)
            }
            RegistrationProgress.Stage.Rejected -> {
                check("registration was rejected" in failure.message!!)
                check("No project model modification was attempted" !in failure.message!!)
            }
        }

        val cancelled = CancellationException("Injected cancellation after $stage")
        val observed = runCatching {
            withRegistrationFailureContext({ _, _ -> error("Cancellation must not be converted") }) { progress ->
                progress.stage = stage
                throw cancelled
            }
        }.exceptionOrNull()
        check(observed === cancelled)
    }

    val outerTimeout = runCatching {
        withTimeout(50) {
            withRegistrationFailureContext({ _, _ -> error("Outer timeout must not be converted") }) { progress ->
                progress.stage = RegistrationProgress.Stage.CommitUnknown
                awaitCancellation()
            }
        }
    }.exceptionOrNull()
    check(outerTimeout is TimeoutCancellationException)

    val fatal = AssertionError("Fatal errors must not become recoverable registration failures")
    val observedFatal = runCatching {
        withRegistrationFailureContext(failureFactory) { throw fatal }
    }.exceptionOrNull()
    check(observedFatal === fatal)
}
