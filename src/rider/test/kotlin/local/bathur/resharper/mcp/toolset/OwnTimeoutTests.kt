// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

package local.bathur.resharper.mcp.toolset

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

fun main() = runBlocking {
    val expectedValue = Any()
    check(withOwnTimeout(60_000, { error("Successful call timed out") }) { expectedValue } === expectedValue)
    check(withOwnTimeout<String?>(60_000, { error("Successful null was treated as a timeout") }) { null } == null)

    val ownTimeoutFailure = IllegalStateException("Own timeout")
    var ownTimeoutCalls = 0
    var ownBlockCancelled = false
    val ownFailure = runCatching {
        withOwnTimeout(100, {
            ownTimeoutCalls++
            throw ownTimeoutFailure
        }) {
            try {
                awaitCancellation()
            } finally {
                ownBlockCancelled = true
            }
        }
    }.exceptionOrNull()
    check(ownFailure === ownTimeoutFailure)
    check(ownTimeoutCalls == 1)
    check(ownBlockCancelled)

    var outerTimeoutCalls = 0
    var outerCancellation: Throwable? = null
    val outerResult = withTimeoutOrNull(100) {
        try {
            withOwnTimeout<Unit>(60_000, {
                outerTimeoutCalls++
                error("Outer timeout was attributed to the tool")
            }) { awaitCancellation() }
        } catch (failure: TimeoutCancellationException) {
            outerCancellation = failure
            throw failure
        }
    }
    check(outerResult == null)
    check(outerCancellation is TimeoutCancellationException)
    check(outerTimeoutCalls == 0)

    var innerTimeoutCalls = 0
    var originalInnerTimeout: Throwable? = null
    val innerFailure = runCatching {
        withOwnTimeout(60_000, {
            innerTimeoutCalls++
            error("Nested timeout was attributed to the tool")
        }) {
            try {
                withTimeout(100) { awaitCancellation() }
            } catch (failure: TimeoutCancellationException) {
                originalInnerTimeout = failure
                throw failure
            }
        }
    }.exceptionOrNull()
    check(innerFailure is TimeoutCancellationException)
    check(originalInnerTimeout is TimeoutCancellationException)
    check(innerFailure.message == originalInnerTimeout.message)
    check(innerTimeoutCalls == 0)

    var manualTimeoutCalls = 0
    var manualCancellation: Throwable? = null
    var manualBlockEntered = false
    val manualCause = CancellationException("Consumer cancelled the call")
    val cancelledCall = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            withOwnTimeout(60_000, {
                manualTimeoutCalls++
                error("Manual cancellation was attributed to the tool")
            }) {
                manualBlockEntered = true
                awaitCancellation()
            }
        } catch (failure: CancellationException) {
            manualCancellation = failure
            throw failure
        }
    }
    check(manualBlockEntered)
    cancelledCall.cancel(manualCause)
    cancelledCall.join()
    check(cancelledCall.isCancelled)
    check(manualCancellation is CancellationException)
    check(manualCancellation.message == manualCause.message)
    check(manualTimeoutCalls == 0)

    for (original in listOf(
        IllegalStateException("RD request failed"),
        CancellationException("Nested operation cancelled"),
    )) {
        val caught = runCatching {
            withOwnTimeout(60_000, { error("Failure was converted to a timeout") }) { throw original }
        }.exceptionOrNull()
        check(caught === original)
    }

    println("Own timeout checks passed: values, null, own/outer/nested timeouts, manual cancellation, and faults.")
}
