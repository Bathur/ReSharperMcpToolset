// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

package local.bathur.resharper.mcp.toolset

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.util.Collections
import java.util.IdentityHashMap

/** A detached, bounded snapshot. It never retains a response, Throwable, or IDE object. */
internal class FailureLogSnapshot private constructor(
    private val data: JsonObject,
    private val omissions: List<JsonObject>,
    private val additionalOmissions: Int,
) {
    /** JSON escaping and UTF-8 expansion are accounted for here, on the writer thread. */
    internal fun encode(): ByteArray {
        var copied = data
        var details = omissions
        var extra = additionalOmissions
        var originalBytes: Int? = null
        var textBudget = SnapshotTextBudget / 2
        while (true) {
            val row = LinkedHashMap(copied)
            row["truncation"] = JsonObject(linkedMapOf(
                "applied" to JsonPrimitive(details.isNotEmpty() || extra > 0 || originalBytes != null),
                "details" to JsonArray(details),
                "additional_omission_count" to JsonPrimitive(extra),
            ).apply {
                originalBytes?.let { put("encoded_bytes_before_limit", JsonPrimitive(it)) }
            })
            val bytes = (JsonObject(row).toString() + "\n").toByteArray(Charsets.UTF_8)
            if (bytes.size <= MaxRecordBytes) return bytes
            if (originalBytes == null) originalBytes = bytes.size
            check(textBudget >= 1_024) { "Cannot fit bounded failure log record" }
            val copier = BoundedLogCopy(textBudget)
            copied = copier.json(data, "record") as JsonObject
            val allDetails = omissions + copier.omissions
            details = allDetails.take(MaxOmissionDetails)
            extra = additionalOmissions + copier.additionalOmissions +
                (allDetails.size - MaxOmissionDetails).coerceAtLeast(0)
            textBudget /= 2
        }
    }

    companion object {
        const val MaxRecordBytes = 256 * 1_024
        private const val SnapshotTextBudget = 128 * 1_024
        private const val MaxOmissionDetails = 32
        private val ResultCollections = setOf(
            "symbols", "targets", "base_types", "derived_types", "overridden_members",
            "overriding_members", "references", "findings",
        )

        fun capture(
            tool: String,
            arguments: Map<String, Any?>,
            outcome: String,
            durationMillis: Long,
            pluginVersion: String,
            riderBuild: String,
            projectName: String? = null,
            projectBasePath: String? = null,
            response: JsonObject? = null,
            failure: Throwable? = null,
        ): FailureLogSnapshot {
            val copy = BoundedLogCopy(SnapshotTextBudget)
            val fields = linkedMapOf<String, JsonElement>(
                "schema_version" to JsonPrimitive(1),
                "timestamp" to JsonPrimitive(Instant.now().toString()),
                "tool" to copy.value(tool, "tool"),
                "outcome" to copy.value(outcome, "outcome"),
                "duration_ms" to JsonPrimitive(durationMillis.coerceAtLeast(0)),
                "plugin_version" to copy.value(pluginVersion, "plugin_version"),
                "rider_build" to copy.value(riderBuild, "rider_build"),
            )
            response?.get("status")?.let { fields["status"] = copy.json(it, "status") }
            fields["project"] = copy.value(
                linkedMapOf("name" to projectName, "base_path" to projectBasePath), "project",
            )
            // Preserve invocation inputs before spending the remaining budget on failure details.
            fields["arguments"] = copy.value(arguments, "arguments")
            if (failure != null) fields["failure"] = copy.failure(failure)
            if (response != null) {
                val summary = linkedMapOf<String, JsonElement>()
                val counts = linkedMapOf<String, JsonElement>()
                for ((key, value) in response) {
                    if (key in ResultCollections && value is JsonArray) {
                        counts[key] = JsonPrimitive(value.size)
                    } else if (summary.size < 64) {
                        summary[key] = value
                    } else {
                        copy.omit("response", "entry_limit", response.size, summary.size)
                        break
                    }
                }
                fields["response"] = copy.json(JsonObject(summary), "response")
                if (counts.isNotEmpty()) fields["result_collection_counts"] = JsonObject(counts)
            }
            return FailureLogSnapshot(JsonObject(fields), copy.omissions.toList(), copy.additionalOmissions)
        }
    }
}

/** Limits retained UTF-16 text, structure and traversal before queue admission. */
private class BoundedLogCopy(private var remainingText: Int) {
    val omissions = mutableListOf<JsonObject>()
    var additionalOmissions = 0
        private set
    private var remainingNodes = 2_048
    private var remainingKeyText = 8_192

    fun omit(path: String, reason: String, original: Int?, retained: Int) {
        if (omissions.size >= 32) {
            additionalOmissions++
            return
        }
        omissions += JsonObject(linkedMapOf(
            "path" to JsonPrimitive(prefix(path, 192)),
            "reason" to JsonPrimitive(reason),
            "original_count" to (original?.let(::JsonPrimitive) ?: JsonNull),
            "retained_count" to JsonPrimitive(retained),
        ))
    }

    private fun string(text: String, path: String): JsonPrimitive {
        val result = prefix(text, minOf(64 * 1_024, remainingText))
        remainingText -= result.length
        if (result.length != text.length) omit(path, "utf16_text_limit", text.length, result.length)
        return JsonPrimitive(result)
    }

    private fun key(text: String, path: String): String? {
        val result = prefix(text, 128)
        if (result.length > remainingKeyText) {
            omit(path, "key_budget", text.length, 0)
            return null
        }
        remainingKeyText -= result.length
        if (result.length != text.length) omit(path, "key_length", text.length, result.length)
        return result
    }

    private fun visit(path: String, depth: Int): Boolean {
        if (depth > 12 || remainingNodes <= 0) {
            omit(path, "structure_limit", 1, 0)
            return false
        }
        remainingNodes--
        return true
    }

    fun value(value: Any?, path: String, depth: Int = 0): JsonElement {
        if (!visit(path, depth)) return JsonNull
        return when (value) {
            null -> JsonNull
            is String -> string(value, path)
            is Boolean -> JsonPrimitive(value)
            is Byte -> JsonPrimitive(value)
            is Short -> JsonPrimitive(value)
            is Int -> JsonPrimitive(value)
            is Long -> JsonPrimitive(value)
            is Float -> if (value.isFinite()) JsonPrimitive(value) else string(value.toString(), path)
            is Double -> if (value.isFinite()) JsonPrimitive(value) else string(value.toString(), path)
            is List<*> -> {
                val count = minOf(value.size, 128, remainingNodes)
                if (count != value.size) omit(path, "entry_limit", value.size, count)
                JsonArray((0 until count).map { value(value[it], "$path[$it]", depth + 1) })
            }
            is Map<*, *> -> {
                val result = linkedMapOf<String, JsonElement>()
                var visited = 0
                for ((key, item) in value) {
                    if (visited++ >= 128 || remainingNodes <= 0) break
                    if (key is String) {
                        val copiedKey = key(key, "$path.<key>") ?: break
                        if (copiedKey in result) {
                            omit(path, "duplicate_truncated_key", 1, 0)
                            continue
                        }
                        result[copiedKey] = value(item, "$path.$copiedKey", depth + 1)
                    }
                }
                if (result.size != value.size) omit(path, "entry_limit", value.size, result.size)
                JsonObject(result)
            }
            else -> {
                // Never invoke arbitrary toString implementations or retain caller-owned objects.
                omit(path, "unsupported_value_type", 1, 0)
                JsonNull
            }
        }
    }

    fun json(value: JsonElement, path: String, depth: Int = 0): JsonElement {
        if (!visit(path, depth)) return JsonNull
        return when (value) {
            is JsonNull -> JsonNull
            is JsonPrimitive -> if (value.isString) string(value.content, path) else {
                // Primitive numbers/booleans in the tool responses are small, but never retain
                // an unbounded token if a future response adds an unusual numeric value.
                if (value.content.length <= 128) value else {
                    omit(path, "primitive_limit", value.content.length, 0)
                    JsonNull
                }
            }
            is JsonArray -> {
                val count = minOf(value.size, 128, remainingNodes)
                if (count != value.size) omit(path, "entry_limit", value.size, count)
                JsonArray((0 until count).map { json(value[it], "$path[$it]", depth + 1) })
            }
            is JsonObject -> {
                val result = linkedMapOf<String, JsonElement>()
                var visited = 0
                for ((key, item) in value) {
                    if (visited++ >= 128 || remainingNodes <= 0) break
                    val copiedKey = key(key, "$path.<key>") ?: break
                    if (copiedKey in result) {
                        omit(path, "duplicate_truncated_key", 1, 0)
                        continue
                    }
                    result[copiedKey] = json(item, "$path.$copiedKey", depth + 1)
                }
                if (result.size != value.size) omit(path, "entry_limit", value.size, result.size)
                JsonObject(result)
            }
        }
    }

    fun failure(failure: Throwable): JsonElement {
        val chain = mutableListOf<JsonElement>()
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = failure
        while (current != null && chain.size < 4 && seen.add(current)) {
            val index = chain.size
            val frames = current.stackTrace
            val keptFrames = minOf(frames.size, 64)
            if (keptFrames != frames.size) omit("failure[$index].stack", "entry_limit", frames.size, keptFrames)
            val fields = linkedMapOf<String, JsonElement>(
                "type" to string(current.javaClass.name, "failure[$index].type"),
                "message" to value(current.message, "failure[$index].message"),
                "stack" to JsonArray((0 until keptFrames).map {
                    string(frames[it].toString(), "failure[$index].stack[$it]")
                }),
                "suppressed_count" to JsonPrimitive(current.suppressed.size),
            )
            chain += JsonObject(fields)
            current = current.cause
        }
        if (current != null) omit("failure", "cause_limit_or_cycle", null, chain.size)
        return JsonArray(chain)
    }
}

/** Avoid splitting a valid surrogate pair when limiting Java/Kotlin strings. */
private fun prefix(text: String, limit: Int): String {
    var end = minOf(text.length, limit.coerceAtLeast(0))
    if (end in 1 until text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
    return if (end == text.length) text else text.substring(0, end)
}
