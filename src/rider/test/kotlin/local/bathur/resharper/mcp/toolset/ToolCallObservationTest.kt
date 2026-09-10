// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

package local.bathur.resharper.mcp.toolset

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun runToolObservationTests() = runBlocking {
    val expectedResult = Any()
    val ok = outcome("ok")
    var successfulRecords = 0
    var successfulArguments = 0
    check(observeToolCall(
        toolName = "successful_tool",
        arguments = { successfulArguments++; emptyMap() },
        render = { check(it === ok); expectedResult },
        record = { _, arguments, _, _, _ -> successfulRecords++; arguments() },
        block = { ok },
    ) === expectedResult)
    check(successfulRecords == 0)
    check(successfulArguments == 0)

    for (status in listOf("partial", "not_found", "ambiguous", "not_indexed", "unsupported")) {
        val response = outcome(status)
        var recordCount = 0
        var argumentCount = 0
        var recorded: ObservedCall? = null
        val result = observeToolCall(
            toolName = "non_ok_tool",
            arguments = { argumentCount++; mapOf("file_path" to "C:/source/Probe.cpp") },
            render = { check(recordCount == 0); expectedResult },
            record = { tool, arguments, duration, observed, failure ->
                recordCount++
                recorded = ObservedCall(tool, arguments(), duration, observed, failure)
            },
            block = { response },
        )
        check(result === expectedResult)
        check(recordCount == 1)
        check(argumentCount == 1)
        check(recorded?.tool == "non_ok_tool")
        check(recorded?.arguments?.get("file_path") == "C:/source/Probe.cpp")
        check(checkNotNull(recorded).duration >= 0)
        check(recorded?.response === response)
        check(recorded?.failure == null)
    }

    // The logger is allowed to drop a record, including when preparing arguments fails.
    check(observeToolCall(
        toolName = "non_ok_tool",
        arguments = { error("Argument preparation failed") },
        render = { expectedResult },
        record = { _, arguments, _, _, _ -> arguments(); error("Unexpected") },
        block = { outcome("partial") },
    ) === expectedResult)

    val failures = listOf(
        IllegalArgumentException("validator rejected an argument"),
        IllegalStateException("RD request failed"),
        CancellationException("consumer cancelled"),
    )
    for (original in failures) {
        var recordCount = 0
        var recorded: ObservedCall? = null
        val caught = runCatching {
            observeToolCall(
                toolName = "failing_tool",
                arguments = { emptyMap() },
                render = { error("Failure must not render a response") },
                record = { tool, arguments, duration, response, failure ->
                    recordCount++
                    recorded = ObservedCall(tool, arguments(), duration, response, failure)
                    throw CancellationException("Log callback failed independently")
                },
                block = { throw original },
            )
        }.exceptionOrNull()
        check(caught === original)
        check(recordCount == 1)
        check(recorded?.response == null)
        check(recorded?.failure === original)
    }

    for (status in listOf("ok", "partial")) {
        val response = outcome(status)
        val original = IllegalStateException("Response serialization failed")
        var recordCount = 0
        var recorded: ObservedCall? = null
        val caught = runCatching {
            observeToolCall(
                toolName = "render_failure",
                arguments = { emptyMap() },
                render = { throw original },
                record = { tool, arguments, duration, observed, failure ->
                    recordCount++
                    recorded = ObservedCall(tool, arguments(), duration, observed, failure)
                },
                block = { response },
            )
        }.exceptionOrNull()
        check(caught === original)
        check(recordCount == 1)
        check(recorded?.response === response)
        check(recorded?.failure === original)
    }

    var cancellationRecord: Throwable? = null
    var cancellationResponse: JsonObject? = ok
    var cancellationCaught: Throwable? = null
    val cancelledCall = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            observeToolCall(
                toolName = "suspended_tool",
                arguments = { emptyMap() },
                render = { error("Cancelled call must not render") },
                record = { _, _, _, response, failure ->
                    cancellationResponse = response
                    cancellationRecord = failure
                },
                block = { awaitCancellation() },
            )
        } catch (failure: CancellationException) {
            cancellationCaught = failure
            throw failure
        }
    }
    cancelledCall.cancelAndJoin()
    check(cancellationCaught is CancellationException)
    check(cancellationRecord === cancellationCaught)
    check(cancellationResponse == null)
}

private data class ObservedCall(
    val tool: String,
    val arguments: Map<String, Any?>,
    val duration: Long,
    val response: JsonObject?,
    val failure: Throwable?,
)

private fun outcome(status: String): JsonObject = buildJsonObject { put("status", status) }
