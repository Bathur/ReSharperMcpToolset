// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

package local.bathur.resharper.mcp.toolset

import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun <T> withOwnTimeout(
    timeoutMs: Long,
    onTimeout: () -> Nothing,
    block: suspend () -> T,
): T {
    // Only this scope's timeout returns null; a successful null value stays boxed.
    val completed = withTimeoutOrNull(timeoutMs) { CompletedValue(block()) }
    return (completed ?: onTimeout()).value
}

private class CompletedValue<T>(val value: T)
