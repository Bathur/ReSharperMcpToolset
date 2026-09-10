// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

package local.bathur.resharper.mcp.toolset

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Observes only the final outcome, without adding suspension or changing exceptions. */
internal suspend fun <T> observeToolCall(
    toolName: String,
    arguments: () -> Map<String, Any?>,
    render: (JsonObject) -> T,
    record: (String, () -> Map<String, Any?>, Long, JsonObject?, Throwable?) -> Unit,
    block: suspend () -> JsonObject,
): T {
    val startedAt = System.nanoTime()
    var response: JsonObject? = null
    val result = try {
        block().let {
            response = it
            render(it)
        }
    } catch (failure: Throwable) {
        recordSafely(toolName, arguments, startedAt, response, failure, record)
        throw failure
    }

    if ((response?.get("status") as? JsonPrimitive)?.content != "ok") {
        recordSafely(toolName, arguments, startedAt, response, null, record)
    }
    return result
}

private fun recordSafely(
    toolName: String,
    arguments: () -> Map<String, Any?>,
    startedAt: Long,
    response: JsonObject?,
    failure: Throwable?,
    record: (String, () -> Map<String, Any?>, Long, JsonObject?, Throwable?) -> Unit,
) {
    try {
        record(toolName, arguments, (System.nanoTime() - startedAt) / 1_000_000, response, failure)
    } catch (_: Throwable) {
        // Logging is best effort, including during cancellation and IDE shutdown.
    }
}
