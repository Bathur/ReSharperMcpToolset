// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

package local.bathur.resharper.mcp.toolset

import com.intellij.mcpserver.McpToolCallResult
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.util.resolveInProject
import com.jetbrains.rider.projectView.solution
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Paths

class ReSharperMcpToolset : McpToolset {
    @McpTool
    @McpDescription(
        """
        Resolves the C++ semantic target at a 1-based physical source position. The target identifier may be a declaration, definition, or Rider-resolvable reference/use; at calls, member accesses, and type uses, place the column on the callee, member, or type identifier.
        Returns semantic identity, kind, signature, containing type/module, declarations, and definitions. A resolved overloaded call returns the concrete overload selected by Rider; ambiguous positions return candidates without choosing.
        Prefer navigation returned by resharper_cpp_search_symbols, resharper_cpp_list_symbols_in_file, or a hierarchy tool. This is identity lookup, not usages or hierarchy, and never falls back to text search.
        """
    )
    suspend fun resharper_cpp_inspect_symbol(
        @McpDescription("Project-relative or absolute Rider-indexed C++ file path")
        file_path: String,
        @McpDescription("1-based line containing the target identifier occurrence")
        line: Int,
        @McpDescription("1-based column on a declaration, definition, or Rider-resolvable reference identifier")
        column: Int,
        @McpDescription("Timeout in milliseconds; default 60000, range 1000..600000")
        timeout_ms: Int = 60_000,
    ): McpToolCallResult = withLoggedCall("resharper_cpp_inspect_symbol", {
        mapOf("file_path" to file_path, "line" to line, "column" to column, "timeout_ms" to timeout_ms)
    }) {
        validatePosition(line, column)
        val project = currentCoroutineContext().project
        val response = withCppToolTimeout(timeout_ms, 600_000, "symbol inspection") {
            project.solution.bathurReSharperMcpToolsetModel.inspectSymbol.startSuspending(
                CppTargetRequest(
                    filePath = project.resolveCppSourcePath(file_path),
                    line = line,
                    column = column,
                )
            )
        }

        buildJsonObject {
            put("status", response.status)
            put("symbols", JsonArray(response.symbols.map(::inspectedSymbolJson)))
            put("search_scope", response.searchScope)
            put("diagnostics", diagnosticsJson(response.diagnostics))
        }
    }

    @McpTool
    @McpDescription(
        """
        Searches Rider's exact, case-sensitive C++ index by short or qualified name across the solution and Rider-known Engine/libraries.
        It is not fuzzy; use file/text/regex discovery when spelling is unknown. Short names may return overload or scope candidates, so confirm qualified_name/signature and pass navigation to resharper_cpp_inspect_symbol, resharper_cpp_find_references, or the appropriate hierarchy tool.
        Filter with normalized kinds. Continue stateless pagination with next_offset.
        """
    )
    suspend fun resharper_cpp_search_symbols(
        @McpDescription("Exact case-sensitive C++ short or qualified name")
        name: String,
        @McpDescription("Normalized kind filters such as class, method, field, or namespace; empty means all")
        kinds: List<String> = emptyList(),
        @McpDescription("0-based offset; default 0, use next_offset to continue")
        offset: Int = 0,
        @McpDescription("Page size; default 100, range 1..200")
        max_results: Int = 100,
        @McpDescription("Timeout in milliseconds; default 60000, range 1000..600000")
        timeout_ms: Int = 60_000,
    ): McpToolCallResult = withLoggedCall("resharper_cpp_search_symbols", {
        mapOf(
            "name" to name, "kinds" to kinds, "offset" to offset,
            "max_results" to max_results, "timeout_ms" to timeout_ms,
        )
    }) {
        if (name.isBlank()) mcpFail("name must not be blank")
        validatePaging(offset, max_results)
        val project = currentCoroutineContext().project
        val response = withCppToolTimeout(timeout_ms, 600_000, "exact symbol search") {
            project.solution.bathurReSharperMcpToolsetModel.searchSymbols.startSuspending(
                SearchSymbolsRequest(
                    name = name.trim(),
                    kinds = kinds.filter(String::isNotBlank).map { it.trim().lowercase() }.toTypedArray(),
                    offset = offset,
                    maxResults = max_results,
                )
            )
        }

        buildJsonObject {
            put("status", response.status)
            put("symbols", JsonArray(response.symbols.map(::symbolSummaryJson)))
            put("page", pageJson(response.page))
            put("search_scope", response.searchScope)
            put("diagnostics", diagnosticsJson(response.diagnostics))
        }
    }

    @McpTool
    @McpDescription(
        """
        Lists source-ordered physical C++ declaration/definition occurrences in one indexed file; use it when the file is known but the name or position is not.
        Results are outline occurrences, not canonical symbols, and preserve hierarchy across pages. Locals, parameters, preprocessor/include/macro-synthetic declarations are excluded.
        Pass a location to resharper_cpp_inspect_symbol for canonical identity and all declarations/definitions; partial means some occurrences were skipped.
        """
    )
    suspend fun resharper_cpp_list_symbols_in_file(
        @McpDescription("Project-relative or absolute Rider-indexed C++ file path")
        file_path: String,
        @McpDescription("0-based occurrence offset; default 0, use next_offset to continue")
        offset: Int = 0,
        @McpDescription("Page size; default 100, range 1..200")
        max_results: Int = 100,
        @McpDescription("Timeout in milliseconds; default 30000, range 1000..120000")
        timeout_ms: Int = 30_000,
    ): McpToolCallResult = withLoggedCall("resharper_cpp_list_symbols_in_file", {
        mapOf("file_path" to file_path, "offset" to offset, "max_results" to max_results, "timeout_ms" to timeout_ms)
    }) {
        validatePaging(offset, max_results)
        val project = currentCoroutineContext().project
        val response = withCppToolTimeout(timeout_ms, 120_000, "file symbol outline") {
            project.solution.bathurReSharperMcpToolsetModel.listSymbolsInFile.startSuspending(
                CppFileOutlineRequest(
                    filePath = project.resolveCppSourcePath(file_path),
                    offset = offset,
                    maxResults = max_results,
                )
            )
        }

        buildJsonObject {
            put("status", response.status)
            put("file_path", response.filePath)
            if (response.ueModule.isNotEmpty()) put("ue_module", response.ueModule)
            put("psi_context", response.psiContext)
            put("symbols", JsonArray(response.symbols.map(::fileSymbolOccurrenceJson)))
            put("page", pageJson(response.page))
            put("diagnostics", diagnosticsJson(response.diagnostics))
        }
    }

    @McpTool
    @McpDescription(
        """
        Runs a fresh headless ReSharper C++ daemon analysis for one indexed file; use it instead of generic get_file_problems for C++/Unreal.
        Rider's normal stages and effective severity settings apply, including UHT when applicable. Findings are Rider's current diagnostics, not a build verdict, and no Quick Fix is performed.
        Code findings and query-completeness diagnostics are separate; ranges are 1-based and end-exclusive. Position and severity are post-filters; pagination reruns the full analysis.
        """
    )
    suspend fun resharper_cpp_get_diagnostics(
        @McpDescription("Project-relative or absolute Rider-indexed C++ file path")
        file_path: String,
        @McpDescription("1-based point-filter line; requires column")
        line: Int? = null,
        @McpDescription("1-based point-filter column; requires line")
        column: Int? = null,
        @McpDescription("Minimum effective severity; default warning; error, warning, suggestion, hint, or info")
        min_severity: String = "warning",
        @McpDescription("0-based finding offset; default 0, use next_offset to continue")
        offset: Int = 0,
        @McpDescription("Page size; default 100, range 1..200")
        max_results: Int = 100,
        @McpDescription("Timeout in milliseconds; default 60000, range 1000..600000")
        timeout_ms: Int = 60_000,
    ): McpToolCallResult = withLoggedCall("resharper_cpp_get_diagnostics", {
        mapOf(
            "file_path" to file_path, "line" to line, "column" to column,
            "min_severity" to min_severity, "offset" to offset,
            "max_results" to max_results, "timeout_ms" to timeout_ms,
        )
    }) {
        if ((line == null) != (column == null)) {
            mcpFail("line and column must be provided together")
        }
        if (line != null && column != null) validatePosition(line, column)
        val normalizedSeverity = min_severity.trim().lowercase()
        if (normalizedSeverity !in setOf("error", "warning", "suggestion", "hint", "info")) {
            mcpFail("min_severity must be one of error, warning, suggestion, hint, or info")
        }
        validatePaging(offset, max_results)

        val project = currentCoroutineContext().project
        val response = withCppToolTimeout(timeout_ms, 600_000, "file diagnostics") {
            project.solution.bathurReSharperMcpToolsetModel.getDiagnostics.startSuspending(
                CppDiagnosticsRequest(
                    filePath = project.resolveCppSourcePath(file_path),
                    hasPosition = line != null,
                    line = line ?: 0,
                    column = column ?: 0,
                    minSeverity = normalizedSeverity,
                    offset = offset,
                    maxResults = max_results,
                )
            )
        }

        buildJsonObject {
            put("status", response.status)
            put("file_path", response.filePath)
            put("psi_context", response.psiContext)
            if (response.primaryPsiLanguage.isNotEmpty()) {
                put("primary_psi_language", response.primaryPsiLanguage)
            }
            if (response.indexEvidence.isNotEmpty()) put("index_evidence", response.indexEvidence)
            put(
                "query",
                buildJsonObject {
                    put("min_severity", normalizedSeverity)
                    if (line != null && column != null) {
                        put(
                            "position",
                            buildJsonObject {
                                put("line", line)
                                put("column", column)
                            }
                        )
                    }
                }
            )
            if (response.hasSourceFile) {
                put("source_file", diagnosticsSourceFileJson(response.sourceFile))
            }
            if (response.hasDaemon) put("daemon", diagnosticsDaemonJson(response.daemon))
            put("findings", JsonArray(response.findings.map(::diagnosticFindingJson)))
            put("page", pageJson(response.page))
            put("findings_metadata", diagnosticsMetadataJson(response.findingsMetadata))
            put("diagnostics", diagnosticsJson(response.diagnostics))
        }
    }

    @McpTool
    @McpDescription(
        """
        Registers one existing, unregistered ordinary C++ file under an explicit already-registered Rider project-tree parent using Add Existing Item.
        Use after external patch/copy causes file-based semantic tools to report psi_source_not_registered; absence from global symbol search alone is insufficient.
        It may create project folder/filter nodes for missing physical subdirectories, but never creates, edits, copies, moves, links, or deletes content. The file must be a strict descendant; directories and recursive import are unsupported.
        The operation is idempotent: retry identical arguments after an unknown timeout, and use project_name only for returned ambiguity. semantic_ready covers the project item and primary C++ PSI/code model, not every global name cache.
        """
    )
    suspend fun resharper_cpp_add_existing_file(
        @McpDescription("Physical directory already represented by a Rider project-tree node")
        parent_directory: String,
        @McpDescription("Existing ordinary C++ file strictly below parent_directory")
        file_path: String,
        @McpDescription("Exact case-sensitive project name; use only to resolve returned ambiguity")
        project_name: String? = null,
        @McpDescription("Registration timeout in milliseconds; default 30000, range 1000..120000")
        timeout_ms: Int = 30_000,
    ): McpToolCallResult = withLoggedCall("resharper_cpp_add_existing_file", {
        mapOf(
            "parent_directory" to parent_directory, "file_path" to file_path,
            "project_name" to project_name, "timeout_ms" to timeout_ms,
        )
    }) {
        val project = currentCoroutineContext().project
        val response = ExistingCppFileRegistration(project).register(
            parentDirectoryInput = parent_directory,
            filePathInput = file_path,
            projectName = project_name,
            timeoutMs = timeout_ms,
        )
        response
    }

    @McpTool
    @McpDescription(
        """
        Returns only direct C++ base types at an exact 1-based type position. Repeat on returned base navigation to walk upward; use resharper_cpp_find_derived_types for the downward transitive closure.
        Ambiguous positions do not query, and names/text are never used to guess. Continue stateless pagination with next_offset; partial means mapped results are incomplete.
        """
    )
    suspend fun resharper_cpp_get_direct_base_types(
        @McpDescription("Project-relative or absolute Rider-indexed C++ file path")
        file_path: String,
        @McpDescription("1-based line on the exact target type identifier")
        line: Int,
        @McpDescription("1-based column on the exact target type identifier")
        column: Int,
        @McpDescription("0-based offset; default 0, use next_offset to continue")
        offset: Int = 0,
        @McpDescription("Page size; default 100, range 1..200")
        max_results: Int = 100,
        @McpDescription("Timeout in milliseconds; default 60000, range 1000..600000")
        timeout_ms: Int = 60_000,
    ): McpToolCallResult = withLoggedCall("resharper_cpp_get_direct_base_types", {
        mapOf(
            "file_path" to file_path, "line" to line, "column" to column,
            "offset" to offset, "max_results" to max_results, "timeout_ms" to timeout_ms,
        )
    }) {
        validatePosition(line, column)
        validatePaging(offset, max_results)
        val project = currentCoroutineContext().project
        val response = withCppToolTimeout(timeout_ms, 600_000, "direct base type query") {
            project.solution.bathurReSharperMcpToolsetModel.getDirectBaseTypes.startSuspending(
                CppPagedTargetRequest(
                    filePath = project.resolveCppSourcePath(file_path),
                    line = line,
                    column = column,
                    offset = offset,
                    maxResults = max_results,
                )
            )
        }

        buildJsonObject {
            put("status", response.status)
            put("targets", JsonArray(response.targets.map(::symbolSummaryJson)))
            put("base_types", JsonArray(response.baseTypes.map(::baseTypeJson)))
            put("page", pageJson(response.page))
            put("search_scope", response.searchScope)
            put("diagnostics", diagnosticsJson(response.diagnostics))
        }
    }

    @McpTool
    @McpDescription(
        """
        Finds Rider-known transitive C++ source descendants or implementations at an exact 1-based type position.
        Results are flat, not a hierarchy tree; relation is direct/indirect only when proven, and order does not imply path or depth. Blueprint/asset inheritors belong to Unreal MCP.
        Ambiguous positions do not query. Continue stateless pagination with next_offset; partial means mapped results are incomplete.
        """
    )
    suspend fun resharper_cpp_find_derived_types(
        @McpDescription("Project-relative or absolute Rider-indexed C++ file path")
        file_path: String,
        @McpDescription("1-based line on the exact target type identifier")
        line: Int,
        @McpDescription("1-based column on the exact target type identifier")
        column: Int,
        @McpDescription("0-based offset; default 0, use next_offset to continue")
        offset: Int = 0,
        @McpDescription("Page size; default 100, range 1..200")
        max_results: Int = 100,
        @McpDescription("Timeout in milliseconds; default 60000, range 1000..600000")
        timeout_ms: Int = 60_000,
    ): McpToolCallResult = withLoggedCall("resharper_cpp_find_derived_types", {
        mapOf(
            "file_path" to file_path, "line" to line, "column" to column,
            "offset" to offset, "max_results" to max_results, "timeout_ms" to timeout_ms,
        )
    }) {
        validatePosition(line, column)
        validatePaging(offset, max_results)
        val project = currentCoroutineContext().project
        val response = withCppToolTimeout(timeout_ms, 600_000, "derived type query") {
            project.solution.bathurReSharperMcpToolsetModel.findDerivedTypes.startSuspending(
                CppPagedTargetRequest(
                    filePath = project.resolveCppSourcePath(file_path),
                    line = line,
                    column = column,
                    offset = offset,
                    maxResults = max_results,
                )
            )
        }

        buildJsonObject {
            put("status", response.status)
            put("targets", JsonArray(response.targets.map(::symbolSummaryJson)))
            put("derived_types", JsonArray(response.symbols.map(::relatedSymbolJson)))
            put("page", pageJson(response.page))
            put("search_scope", response.searchScope)
            put("diagnostics", diagnosticsJson(response.diagnostics))
        }
    }

    @McpTool
    @McpDescription(
        """
        Returns the nearest base member(s) directly overridden by an exact C++ function/method position. Repeat on returned navigation to walk upward; multiple inheritance may return several results.
        This is not declaration-to-definition lookup (use resharper_cpp_inspect_symbol), and it never matches names/signatures heuristically. Ambiguous positions do not query.
        Continue stateless pagination with next_offset; partial means mapped results are incomplete.
        """
    )
    suspend fun resharper_cpp_get_direct_overridden_members(
        @McpDescription("Project-relative or absolute Rider-indexed C++ file path")
        file_path: String,
        @McpDescription("1-based line on the exact target function or method identifier")
        line: Int,
        @McpDescription("1-based column on the exact target function or method identifier")
        column: Int,
        @McpDescription("0-based offset; default 0, use next_offset to continue")
        offset: Int = 0,
        @McpDescription("Page size; default 100, range 1..200")
        max_results: Int = 100,
        @McpDescription("Timeout in milliseconds; default 60000, range 1000..600000")
        timeout_ms: Int = 60_000,
    ): McpToolCallResult = withLoggedCall("resharper_cpp_get_direct_overridden_members", {
        mapOf(
            "file_path" to file_path, "line" to line, "column" to column,
            "offset" to offset, "max_results" to max_results, "timeout_ms" to timeout_ms,
        )
    }) {
        validatePosition(line, column)
        validatePaging(offset, max_results)
        val project = currentCoroutineContext().project
        val response = withCppToolTimeout(timeout_ms, 600_000, "direct overridden member query") {
            project.solution.bathurReSharperMcpToolsetModel.getDirectOverriddenMembers.startSuspending(
                CppPagedTargetRequest(
                    filePath = project.resolveCppSourcePath(file_path),
                    line = line,
                    column = column,
                    offset = offset,
                    maxResults = max_results,
                )
            )
        }

        buildJsonObject {
            put("status", response.status)
            put("targets", JsonArray(response.targets.map(::symbolSummaryJson)))
            put("overridden_members", JsonArray(response.symbols.map(::relatedSymbolJson)))
            put("page", pageJson(response.page))
            put("search_scope", response.searchScope)
            put("diagnostics", diagnosticsJson(response.diagnostics))
        }
    }

    @McpTool
    @McpDescription(
        """
        Finds Rider-known transitive downstream C++ overrides/implementations of an exact virtual, pure-virtual, or interface member.
        Results are flat, not a call graph or hierarchy tree; relation is direct/indirect only when proven, and order does not imply path or depth. Blueprint implementations belong to Unreal MCP.
        Ambiguous positions do not query. Continue stateless pagination with next_offset; partial means mapped results are incomplete.
        """
    )
    suspend fun resharper_cpp_find_overriding_members(
        @McpDescription("Project-relative or absolute Rider-indexed C++ file path")
        file_path: String,
        @McpDescription("1-based line on the exact target virtual function or method identifier")
        line: Int,
        @McpDescription("1-based column on the exact target virtual function or method identifier")
        column: Int,
        @McpDescription("0-based offset; default 0, use next_offset to continue")
        offset: Int = 0,
        @McpDescription("Page size; default 100, range 1..200")
        max_results: Int = 100,
        @McpDescription("Timeout in milliseconds; default 60000, range 1000..600000")
        timeout_ms: Int = 60_000,
    ): McpToolCallResult = withLoggedCall("resharper_cpp_find_overriding_members", {
        mapOf(
            "file_path" to file_path, "line" to line, "column" to column,
            "offset" to offset, "max_results" to max_results, "timeout_ms" to timeout_ms,
        )
    }) {
        validatePosition(line, column)
        validatePaging(offset, max_results)
        val project = currentCoroutineContext().project
        val response = withCppToolTimeout(timeout_ms, 600_000, "overriding member query") {
            project.solution.bathurReSharperMcpToolsetModel.findOverridingMembers.startSuspending(
                CppPagedTargetRequest(
                    filePath = project.resolveCppSourcePath(file_path),
                    line = line,
                    column = column,
                    offset = offset,
                    maxResults = max_results,
                )
            )
        }

        buildJsonObject {
            put("status", response.status)
            put("targets", JsonArray(response.targets.map(::symbolSummaryJson)))
            put("overriding_members", JsonArray(response.symbols.map(::relatedSymbolJson)))
            put("page", pageJson(response.page))
            put("search_scope", response.searchScope)
            put("diagnostics", diagnosticsJson(response.diagnostics))
        }
    }

    @McpTool
    @McpDescription(
        """
        Finds semantic usages of an exact C++ declared element at a 1-based source position.
        Direct means exact symbol identity, not a call-graph hop; results are not callers/callees and do not merge base/overriding members. Ambiguous positions do not query, and text search is never a fallback. usage_kind may be unknown.
        Timeout cancels the whole query without returning incomplete references; raise timeout_ms for intentional large searches. Continue stateless pagination with next_offset; partial means mapping was incomplete.
        """
    )
    suspend fun resharper_cpp_find_references(
        @McpDescription("Project-relative or absolute Rider-indexed C++ file path")
        file_path: String,
        @McpDescription("1-based line on the exact target identifier")
        line: Int,
        @McpDescription("1-based column on the exact target identifier")
        column: Int,
        @McpDescription("0-based offset; default 0, use next_offset to continue")
        offset: Int = 0,
        @McpDescription("Page size; default 100, range 1..200")
        max_results: Int = 100,
        @McpDescription("Timeout in milliseconds; default 60000, range 1000..600000")
        timeout_ms: Int = 60_000,
    ): McpToolCallResult = withLoggedCall("resharper_cpp_find_references", {
        mapOf(
            "file_path" to file_path, "line" to line, "column" to column,
            "offset" to offset, "max_results" to max_results, "timeout_ms" to timeout_ms,
        )
    }) {
        validatePosition(line, column)
        validatePaging(offset, max_results)
        val project = currentCoroutineContext().project
        val response = withCppToolTimeout(timeout_ms, 600_000, "reference search") {
            project.solution.bathurReSharperMcpToolsetModel.findReferences.startSuspending(
                CppPagedTargetRequest(
                    filePath = project.resolveCppSourcePath(file_path),
                    line = line,
                    column = column,
                    offset = offset,
                    maxResults = max_results,
                )
            )
        }

        val references = JsonArray(response.references.map { reference ->
            buildJsonObject {
                put("location", sourcePositionJson(reference.location))
                put("context", reference.context)
                put("usage_kind", reference.usageKind)
            }
        })

        buildJsonObject {
            put("status", response.status)
            put("targets", JsonArray(response.targets.map(::symbolSummaryJson)))
            put("references", references)
            put("page", pageJson(response.page))
            put("search_scope", response.searchScope)
            put("diagnostics", diagnosticsJson(response.diagnostics))
        }
    }

    private suspend fun withLoggedCall(
        toolName: String,
        arguments: () -> Map<String, Any?>,
        block: suspend () -> JsonObject,
    ): McpToolCallResult {
        val context = currentCoroutineContext()
        return observeToolCall(toolName, arguments, ::responseResult, { tool, args, duration, response, failure ->
            McpFailureLog.record(tool, args, duration, response, failure, projectDetails = {
                try {
                    val project = context.project
                    project.name to project.basePath
                } catch (_: Throwable) {
                    // Missing or disposed project context must not hide the original failure.
                    null to null
                }
            })
        }, block)
    }

    private suspend fun <T> withCppToolTimeout(
        timeoutMs: Int,
        maxTimeoutMs: Int,
        operationLabel: String,
        block: suspend () -> T,
    ): T {
        if (timeoutMs !in 1_000..maxTimeoutMs) {
            mcpFail("timeout_ms must be between 1000 and $maxTimeoutMs")
        }

        return try {
            withTimeout(timeoutMs.toLong()) {
                block()
            }
        } catch (_: TimeoutCancellationException) {
            mcpFail(
                "ReSharper C++ $operationLabel timed out after $timeoutMs ms. " +
                    "Retry with a larger timeout_ms when a longer $operationLabel is intentional."
            )
        }
    }

    private fun validatePosition(line: Int, column: Int) {
        if (line < 1) mcpFail("line must be at least 1")
        if (column < 1) mcpFail("column must be at least 1")
    }

    private fun validatePaging(offset: Int, maxResults: Int) {
        if (offset < 0) mcpFail("offset must be zero or greater")
        if (maxResults !in 1..200) mcpFail("max_results must be between 1 and 200")
    }

    private fun com.intellij.openapi.project.Project.resolveCppSourcePath(filePath: String): String {
        val path = Paths.get(filePath)
        return if (path.isAbsolute) {
            path.normalize().toString()
        } else {
            resolveInProject(filePath).toString()
        }
    }

    private fun responseResult(structuredContent: JsonObject): McpToolCallResult =
        McpToolCallResult.text(structuredContent.toString(), structuredContent)

    private fun inspectedSymbolJson(symbol: CppInspectedSymbol): JsonObject = buildJsonObject {
        put("summary", symbolSummaryJson(symbol.summary))
        put("declarations", JsonArray(symbol.declarations.map(::sourcePositionJson)))
        put("definitions", JsonArray(symbol.definitions.map(::sourcePositionJson)))
        put("accessibility", symbol.accessibility)
        put("is_virtual", symbol.isVirtual)
        put("is_pure_virtual", symbol.isPureVirtual)
        put("is_final", symbol.isFinal)
    }

    private fun symbolSummaryJson(symbol: CppSymbolSummary): JsonObject = buildJsonObject {
        put("name", symbol.name)
        put("qualified_name", symbol.qualifiedName)
        put("kind", symbol.kind)
        if (symbol.signature.isNotEmpty()) put("signature", symbol.signature)
        if (symbol.containingType.isNotEmpty()) put("containing_type", symbol.containingType)
        if (symbol.ueModule.isNotEmpty()) put("ue_module", symbol.ueModule)
        if (symbol.hasNavigation) put("navigation", sourcePositionJson(symbol.navigation))
        put("declaration_count", symbol.declarationCount)
        put("definition_count", symbol.definitionCount)
    }

    private fun sourcePositionJson(position: CppSourcePosition): JsonObject = buildJsonObject {
        put("file_path", position.filePath)
        put("line", position.line)
        put("column", position.column)
    }

    private fun baseTypeJson(result: CppBaseTypeResult): JsonObject = buildJsonObject {
        put("symbol", symbolSummaryJson(result.symbol))
        put("accessibility", result.accessibility)
        put("is_virtual", result.isVirtual)
        put("is_implicit", result.isImplicit)
        put("is_pack_expansion", result.isPackExpansion)
    }

    private fun relatedSymbolJson(result: CppRelatedSymbolResult): JsonObject = buildJsonObject {
        put("symbol", symbolSummaryJson(result.symbol))
        put("relation", result.relation)
    }

    private fun fileSymbolOccurrenceJson(symbol: CppFileSymbolOccurrence): JsonObject = buildJsonObject {
        put("outline_index", symbol.outlineIndex)
        put("parent_outline_index", symbol.parentOutlineIndex)
        put("depth", symbol.depth)
        put("name", symbol.name)
        put("qualified_name", symbol.qualifiedName)
        put("kind", symbol.kind)
        if (symbol.signature.isNotEmpty()) put("signature", symbol.signature)
        if (symbol.containingType.isNotEmpty()) put("containing_type", symbol.containingType)
        put("declaration_role", symbol.declarationRole)
        put("location", sourcePositionJson(symbol.location))
    }

    private fun diagnosticsSourceFileJson(sourceFile: CppDiagnosticsSourceFile): JsonObject = buildJsonObject {
        put("is_generated_file", sourceFile.isGeneratedFile)
        put("is_non_user_file", sourceFile.isNonUserFile)
        put("provides_code_model", sourceFile.providesCodeModel)
    }

    private fun diagnosticsDaemonJson(daemon: CppDiagnosticsDaemon): JsonObject = buildJsonObject {
        put("state", daemon.state)
        put("completion_basis", daemon.completionBasis)
        put("process_kind", daemon.processKind)
        put("analysis_scope", daemon.analysisScope)
        put("stage_policy", daemon.stagePolicy)
        put("contributing_stages", JsonArray(daemon.contributingStages.map { JsonPrimitive(it) }))
        put("raw_highlighting_count", daemon.rawHighlightingCount)
    }

    private fun diagnosticFindingJson(finding: CppDiagnosticFinding): JsonObject = buildJsonObject {
        put("severity", finding.severity)
        if (finding.inspectionId.isNotEmpty()) put("inspection_id", finding.inspectionId)
        if (finding.compilerIds.isNotEmpty()) {
            put("compiler_ids", JsonArray(finding.compilerIds.map { JsonPrimitive(it) }))
        }
        put("highlighting_type", finding.highlightingType)
        if (finding.message.isNotEmpty()) put("message", finding.message)
        put("stage", finding.stage)
        put(
            "range",
            buildJsonObject {
                put("file_path", finding.range.filePath)
                put("start_line", finding.range.startLine)
                put("start_column", finding.range.startColumn)
                put("end_line", finding.range.endLine)
                put("end_column", finding.range.endColumn)
                put("end_exclusive", finding.range.endExclusive)
            }
        )
    }

    private fun diagnosticsMetadataJson(metadata: CppDiagnosticsMetadata): JsonObject = buildJsonObject {
        put("raw_highlighting_count", metadata.rawHighlightingCount)
        put("below_severity_count", metadata.belowSeverityCount)
        put("outside_position_count", metadata.outsidePositionCount)
        put("invalid_range_count", metadata.invalidRangeCount)
        put("unreadable_severity_count", metadata.unreadableSeverityCount)
        put("unreadable_metadata_count", metadata.unreadableMetadataCount)
        put("unmapped_range_count", metadata.unmappedRangeCount)
        if (metadata.hasMatchedMappedCount) {
            put("matched_mapped_count", metadata.matchedMappedCount)
        }
    }

    private fun pageJson(page: CppPageInfo): JsonObject = buildJsonObject {
        put("offset", page.offset)
        put("returned_count", page.returnedCount)
        if (page.hasTotalMappedCount) put("total_mapped_count", page.totalMappedCount)
        put("has_more", page.hasMore)
        if (page.hasMore) put("next_offset", page.nextOffset)
        put("skipped_count", page.skippedCount)
    }

    private fun diagnosticsJson(diagnostics: Array<CppSemanticDiagnostic>): JsonArray =
        JsonArray(diagnostics.map { diagnostic ->
            buildJsonObject {
                put("code", diagnostic.code)
                put("message", diagnostic.message)
            }
        })
}
