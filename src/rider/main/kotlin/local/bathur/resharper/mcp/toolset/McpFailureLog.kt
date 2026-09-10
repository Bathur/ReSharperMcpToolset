// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

package local.bathur.resharper.mcp.toolset

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/** IDE ownership only; file access and serialization belong to the writer's worker. */
@Service(Service.Level.APP)
internal class McpFailureLog : Disposable {
    private val writer = FailureLogWriter(
        directory = { PathManager.getLogDir().resolve("resharper-mcp-toolset") },
        onFailure = { failure ->
            Logger.getInstance(McpFailureLog::class.java).warn(
                "ReSharper MCP call logging stopped for this Rider session; tool execution is unaffected.",
                failure,
            )
        },
    )

    private val pluginVersion by lazy {
        (McpFailureLog::class.java.classLoader as? PluginAwareClassLoader)?.pluginDescriptor?.version ?: "unknown"
    }
    private val riderBuild by lazy { ApplicationInfo.getInstance().build.asString() }

    private fun submit(
        toolName: String,
        arguments: () -> Map<String, Any?>,
        durationMs: Long,
        response: JsonObject?,
        failure: Throwable?,
        projectDetails: () -> Pair<String?, String?>,
    ) {
        if (!writer.canAccept()) return
        val (projectName, projectBasePath) = try {
            projectDetails()
        } catch (_: Throwable) {
            null to null
        }
        writer.tryOffer(
            FailureLogSnapshot.capture(
                tool = toolName,
                arguments = arguments(),
                outcome = when (failure) {
                    null -> "non_ok"
                    is CancellationException -> "cancelled"
                    else -> "tool_error"
                },
                durationMillis = durationMs,
                pluginVersion = pluginVersion,
                riderBuild = riderBuild,
                projectName = projectName,
                projectBasePath = projectBasePath,
                response = response,
                failure = failure,
            )
        )
    }

    override fun dispose() {
        writer.close()
    }

    companion object {
        /** Best effort even on cancellation: never suspend, wait for disk, or replace the tool's result. */
        fun record(
            toolName: String,
            arguments: () -> Map<String, Any?>,
            durationMs: Long,
            response: JsonObject?,
            failure: Throwable?,
            projectDetails: () -> Pair<String?, String?> = { null to null },
        ) {
            try {
                if (!FailureLoggingSettings.isEnabled()) return
                val application = ApplicationManager.getApplication()
                if (application.isDisposed) return
                application.getService(McpFailureLog::class.java).submit(
                    toolName, arguments, durationMs, response, failure, projectDetails,
                )
            } catch (_: Throwable) {
                // Observability is optional. Preserve the original response/exception even if logging breaks.
            }
        }
    }
}
