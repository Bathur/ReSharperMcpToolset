// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

package local.bathur.resharper.mcp.toolset

import kotlinx.coroutines.CancellationException

internal class RegistrationProgress {
    var stage = Stage.NotAttempted

    enum class Stage {
        NotAttempted, CommitUnknown, Accepted, Rejected,
    }

    fun failureContext(): String = when (stage) {
        Stage.NotAttempted -> "No project model modification was attempted."
        Stage.CommitUnknown ->
            "The Rider registration request was attempted, but its commit result is unknown; " +
                "the project model modification may already have committed. $SameRegistrationRetry"
        Stage.Accepted ->
            "Rider accepted registration, but the final verification result is unavailable. $SameRegistrationRetry"
        Stage.Rejected ->
            "Rider reported that registration was rejected; follow-up state verification did not complete."
    }
}

internal const val SameRegistrationRetry =
    "Retry with exactly the same parent_directory, file_path, and project_name; " +
        "the operation is idempotent and will not add a duplicate item."

internal suspend fun <T> withRegistrationFailureContext(
    failure: (String, Exception) -> Exception,
    block: suspend (RegistrationProgress) -> T,
): T {
    val progress = RegistrationProgress()
    return try {
        block(progress)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        throw failure(
            "${error.message ?: error.javaClass.simpleName} ${progress.failureContext()}",
            error,
        )
    }
}
