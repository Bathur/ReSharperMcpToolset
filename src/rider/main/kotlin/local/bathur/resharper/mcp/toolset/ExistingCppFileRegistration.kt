// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

package local.bathur.resharper.mcp.toolset

import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.jetbrains.rider.cpp.fileType.CppFileType
import com.jetbrains.rider.ijent.extensions.toRd
import com.jetbrains.rider.model.RdAddItemData
import com.jetbrains.rider.model.RdAddItemDataResult
import com.jetbrains.rider.model.RdAddItemsCommand
import com.jetbrains.rider.model.projectModelTasks
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.containingProjectEntity
import com.jetbrains.rider.projectView.workspace.getId
import com.jetbrains.rider.projectView.workspace.getProjectModelEntities
import com.jetbrains.rider.projectView.workspace.isParentOf
import com.jetbrains.rider.projectView.workspace.isProject
import com.jetbrains.rider.projectView.workspace.isProjectFile
import com.jetbrains.rider.projectView.workspace.isProjectFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths

internal class ExistingCppFileRegistration(private val project: Project) {
    suspend fun register(
        parentDirectoryInput: String,
        filePathInput: String,
        projectName: String?,
        timeoutMs: Int,
    ): JsonObject {
        if (parentDirectoryInput.isBlank()) mcpFail("parent_directory must not be blank")
        if (filePathInput.isBlank()) mcpFail("file_path must not be blank")
        if (projectName != null && projectName.isBlank()) {
            mcpFail("project_name must be omitted or be a non-blank exact project name")
        }
        if (timeoutMs !in 1_000..120_000) {
            mcpFail("timeout_ms must be between 1000 and 120000")
        }

        val deadlineNanos = System.nanoTime() + timeoutMs.toLong() * 1_000_000L
        val unresolvedParent = resolveInputPath(parentDirectoryInput, "parent_directory")
        val unresolvedFile = resolveInputPath(filePathInput, "file_path")

        if (!Files.exists(unresolvedParent, LinkOption.NOFOLLOW_LINKS)) {
            return preflightFailure(
                "not_found",
                unresolvedFile,
                "parent_directory_not_found",
                "The physical parent directory does not exist: $unresolvedParent"
            )
        }
        if (!Files.isDirectory(unresolvedParent, LinkOption.NOFOLLOW_LINKS)) {
            return preflightFailure(
                "unsupported",
                unresolvedFile,
                "parent_directory_not_directory",
                "parent_directory must resolve to an existing ordinary directory: $unresolvedParent"
            )
        }
        if (!Files.exists(unresolvedFile, LinkOption.NOFOLLOW_LINKS)) {
            return preflightFailure(
                "not_found",
                unresolvedFile,
                "file_not_found",
                "The physical file does not exist: $unresolvedFile"
            )
        }
        if (!Files.isRegularFile(unresolvedFile, LinkOption.NOFOLLOW_LINKS)) {
            val code = if (Files.isDirectory(unresolvedFile, LinkOption.NOFOLLOW_LINKS)) {
                "file_is_directory"
            } else {
                "file_not_regular"
            }
            return preflightFailure(
                "unsupported",
                unresolvedFile,
                code,
                "file_path must resolve to an existing ordinary file: $unresolvedFile"
            )
        }

        val parentDirectory = unresolvedParent.toRealPath(LinkOption.NOFOLLOW_LINKS)
        val filePath = unresolvedFile.toRealPath(LinkOption.NOFOLLOW_LINKS)
        if (filePath == parentDirectory || !filePath.startsWith(parentDirectory)) {
            return preflightFailure(
                "unsupported",
                filePath,
                "file_outside_parent_directory",
                "file_path must be a strict physical descendant of parent_directory."
            )
        }

        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath)
            ?: return preflightFailure(
                "not_indexed",
                filePath,
                "vfs_file_not_available",
                "Rider could not load the existing file; refresh the project and retry."
            )
        if (virtualFile.fileType !is CppFileType) {
            return preflightFailure(
                "unsupported",
                filePath,
                "not_cpp_file_type",
                "Rider does not recognize this file as C/C++ source or header."
            )
        }

        val workspaceModel = WorkspaceModel.getInstance(project)
        val allCandidates = workspaceModel
            .getProjectModelEntities(parentDirectory, project)
            .asSequence()
            .filter { it.isProject() || it.isProjectFolder() }
            .mapNotNull { entity ->
                entity.containingProjectEntity()?.let { projectEntity ->
                    ParentCandidate(entity, projectEntity, parentDirectory)
                }
            }
            .distinctBy { it.entity }
            .sortedWith(
                compareBy<ParentCandidate>(
                    { it.projectName.lowercase() },
                    { it.entityKind },
                    { it.entity.name.lowercase() },
                )
            )
            .toList()

        if (allCandidates.isEmpty()) {
            return preflightFailure(
                "not_indexed",
                filePath,
                "parent_project_entity_not_registered",
                "parent_directory is not a registered project directory."
            )
        }

        val candidates = if (projectName == null) {
            allCandidates
        } else {
            allCandidates.filter { it.projectName == projectName }
        }
        if (candidates.isEmpty()) {
            return preflightFailure(
                "not_found",
                filePath,
                "project_name_not_found",
                "No parent matches project_name; choose from parent_candidates.",
                candidates = allCandidates,
            )
        }
        if (candidates.size > 1) {
            val status = if (projectName == null) "ambiguous" else "unsupported"
            val code = if (projectName == null) {
                "ambiguous_parent_project_entity"
            } else {
                "parent_entity_not_unique"
            }
            return preflightFailure(
                status,
                filePath,
                code,
                if (projectName == null) {
                    "parent_directory belongs to multiple projects; retry with an exact project_name."
                } else {
                    "The selected parent remains ambiguous; choose a uniquely registered parent directory."
                },
                candidates = candidates,
            )
        }

        val parent = candidates.single()
        val parentId = parent.entity.getId(project)
            ?: return preflightFailure(
                "not_indexed",
                filePath,
                "project_model_not_ready",
                "The selected project is not ready; retry after project loading.",
                parent = parent,
            )

        val beforeSemantic = querySemanticState(filePath, deadlineNanos)
            ?: mcpFail(
                "ReSharper C++ add-existing-file preflight timed out after $timeoutMs ms. " +
                    "No project model modification was attempted."
            )
        val before = snapshot(parent, filePath, beforeSemantic)

        if (before.projectItemRegistered) {
            val after = if (before.semanticReady) {
                before
            } else {
                awaitSemanticReady(parent, filePath, deadlineNanos, before)
            }
            return completedResponse(
                filePath,
                parent,
                "already_registered",
                "none",
                before,
                after,
                pendingDiagnostics(after),
            )
        }

        val remainingForAdd = remainingMillis(deadlineNanos)
            ?: mcpFail(
                "ReSharper C++ add-existing-file preflight exhausted timeout_ms before the Rider project model request began. " +
                    "No project model modification was attempted."
            )
        val addCallCompletion = withTimeoutOrNull(remainingForAdd) {
            withContext(Dispatchers.EDT) {
                AddCallCompletion(
                    project.solution.projectModelTasks.addItems.startSuspending(
                        RdAddItemsCommand(
                            listOf(
                                RdAddItemData(
                                    itemLocation = filePath.toRd(),
                                    parentId = parentId,
                                    relativeTo = null,
                                    includeContent = false,
                                )
                            )
                        )
                    ).items.singleOrNull()
                )
            }
        } ?: mcpFail(
            "Rider add-existing-item timed out after $timeoutMs ms before its commit result was known. " +
                "The project model modification may already have committed. Retry with exactly the same " +
                "parent_directory, file_path, and project_name; the operation is idempotent and will not add a duplicate item."
        )
        val addItemResult = addCallCompletion.result

        if (addItemResult == null) {
            val after = currentSnapshotOrBefore(parent, filePath, deadlineNanos, before)
            return completedResponse(
                filePath,
                parent,
                "registration_failed",
                "rider_add_existing_item",
                before,
                after,
                listOf(
                    RegistrationDiagnostic(
                        "missing_add_items_result",
                        "Rider returned no registration result for the file."
                    )
                ),
                forcedStatus = "unsupported",
            )
        }

        if (!addItemResult.success) {
            val after = currentSnapshotOrBefore(parent, filePath, deadlineNanos, before)
            return completedResponse(
                filePath,
                parent,
                "registration_failed",
                "rider_add_existing_item",
                before,
                after,
                listOf(
                    RegistrationDiagnostic(
                        "rider_project_model_rejected",
                        "Rider rejected the file or an intermediate project directory."
                    )
                ),
                forcedStatus = "unsupported",
            )
        }

        val projectItemConfirmedByAddResult = addItemResult.itemId != null
        val initialAfter = currentSnapshotOrBefore(
            parent,
            filePath,
            deadlineNanos,
            before,
            projectItemConfirmedByAddResult,
        )
        val after = if (initialAfter.semanticReady) {
            initialAfter
        } else {
            awaitSemanticReady(
                parent,
                filePath,
                deadlineNanos,
                initialAfter,
                projectItemConfirmedByAddResult,
            )
        }
        return completedResponse(
            filePath,
            parent,
            if (after.semanticReady) "registered" else "registered_semantic_pending",
            "rider_add_existing_item",
            before,
            after,
            pendingDiagnostics(after, addItemResult),
        )
    }

    private fun resolveInputPath(input: String, parameterName: String): Path {
        return try {
            val path = Paths.get(input)
            val resolved = if (path.isAbsolute) path else project.resolveInProject(input)
            resolved.toAbsolutePath().normalize()
        } catch (exception: InvalidPathException) {
            mcpFail("$parameterName is not a valid filesystem path: ${exception.message}")
        }
    }

    private suspend fun querySemanticState(
        filePath: Path,
        deadlineNanos: Long,
    ): CppFileSemanticStateResponse? {
        val remaining = remainingMillis(deadlineNanos) ?: return null
        return withTimeoutOrNull(remaining) {
            project.solution.bathurReSharperMcpToolsetModel.getCppFileSemanticState.startSuspending(
                CppFileSemanticStateRequest(filePath.toString())
            )
        }
    }

    private suspend fun awaitSemanticReady(
        parent: ParentCandidate,
        filePath: Path,
        deadlineNanos: Long,
        initial: RegistrationSnapshot,
        projectItemConfirmed: Boolean = false,
    ): RegistrationSnapshot {
        var last = initial
        var delayMillis = 50L
        while (true) {
            val semanticState = querySemanticState(filePath, deadlineNanos)
                ?: return last.copy(
                    projectItemRegistered = projectItemConfirmed || isProjectItemRegistered(parent, filePath)
                )
            last = snapshot(parent, filePath, semanticState, projectItemConfirmed)
            if (last.semanticReady) return last

            val remaining = remainingMillis(deadlineNanos) ?: return last
            delay(minOf(delayMillis, remaining))
            delayMillis = (delayMillis * 2).coerceAtMost(500L)
        }
    }

    private suspend fun currentSnapshotOrBefore(
        parent: ParentCandidate,
        filePath: Path,
        deadlineNanos: Long,
        before: RegistrationSnapshot,
        projectItemConfirmed: Boolean = false,
    ): RegistrationSnapshot {
        val semanticState = querySemanticState(filePath, deadlineNanos)
            ?: return before.copy(
                projectItemRegistered = projectItemConfirmed || isProjectItemRegistered(parent, filePath)
            )
        return snapshot(parent, filePath, semanticState, projectItemConfirmed)
    }

    private fun snapshot(
        parent: ParentCandidate,
        filePath: Path,
        semanticState: CppFileSemanticStateResponse,
        projectItemConfirmed: Boolean = false,
    ): RegistrationSnapshot = RegistrationSnapshot(
        projectItemRegistered = projectItemConfirmed || isProjectItemRegistered(parent, filePath),
        cppPsiSourceRegistered = semanticState.cppPsiSourceRegistered,
        primaryCppPsiAvailable = semanticState.primaryCppPsiAvailable,
        providesCodeModel = semanticState.providesCodeModel,
    )

    private fun isProjectItemRegistered(parent: ParentCandidate, filePath: Path): Boolean {
        val workspaceModel = WorkspaceModel.getInstance(project)
        return workspaceModel.getProjectModelEntities(filePath, project).any { entity ->
            entity.isProjectFile() &&
                entity.containingProjectEntity() == parent.projectEntity &&
                (parent.entity == parent.projectEntity || parent.entity.isParentOf(entity))
        }
    }

    private fun completedResponse(
        filePath: Path,
        parent: ParentCandidate,
        outcome: String,
        actionPerformed: String,
        before: RegistrationSnapshot,
        after: RegistrationSnapshot,
        diagnostics: List<RegistrationDiagnostic>,
        forcedStatus: String? = null,
    ): JsonObject = buildJsonObject {
        put("status", forcedStatus ?: if (after.semanticReady) "ok" else "partial")
        put("file_path", filePath.toString())
        put("parent", parentJson(parent))
        put("outcome", outcome)
        put("action_performed", actionPerformed)
        put("before", snapshotJson(before))
        put("after", snapshotJson(after))
        put("semantic_ready", after.semanticReady)
        put("verification_scope", VerificationScope)
        put("diagnostics", diagnosticsJson(diagnostics))
    }

    private fun preflightFailure(
        status: String,
        filePath: Path,
        code: String,
        message: String,
        parent: ParentCandidate? = null,
        candidates: List<ParentCandidate> = emptyList(),
    ): JsonObject = buildJsonObject {
        put("status", status)
        put("file_path", filePath.toAbsolutePath().normalize().toString())
        if (parent != null) put("parent", parentJson(parent))
        put("outcome", "not_attempted")
        put("action_performed", "none")
        put("verification_scope", VerificationScope)
        put("diagnostics", diagnosticsJson(listOf(RegistrationDiagnostic(code, message))))
        if (candidates.isNotEmpty()) {
            put("parent_candidates", JsonArray(candidates.map(::parentJson)))
        }
    }

    private fun pendingDiagnostics(
        snapshot: RegistrationSnapshot,
        addItemResult: RdAddItemDataResult? = null,
    ): List<RegistrationDiagnostic> {
        if (snapshot.semanticReady) return emptyList()
        val diagnostics = mutableListOf<RegistrationDiagnostic>()
        if (!snapshot.projectItemRegistered) {
            diagnostics += RegistrationDiagnostic(
                "project_item_not_presented_after_registration",
                if (addItemResult?.itemId == null) {
                    "Registration was accepted, but the project item was not visible before the timeout."
                } else {
                    "The registered project item was not visible before the timeout."
                }
            )
        }
        if (!snapshot.cppPsiSourceRegistered) {
            diagnostics += RegistrationDiagnostic(
                "cpp_psi_source_not_ready",
                "C++ source registration was not ready before the timeout."
            )
        } else if (!snapshot.primaryCppPsiAvailable) {
            diagnostics += RegistrationDiagnostic(
                "primary_cpp_psi_not_ready",
                "C++ semantic data was not ready before the timeout."
            )
        } else if (!snapshot.providesCodeModel) {
            diagnostics += RegistrationDiagnostic(
                "cpp_code_model_not_ready",
                "The C++ code model was not ready before the timeout."
            )
        }
        return diagnostics
    }

    private fun parentJson(candidate: ParentCandidate): JsonObject = buildJsonObject {
        put("directory", candidate.directory.toString())
        put("entity_name", candidate.entity.name)
        put("entity_kind", candidate.entityKind)
        put("project_name", candidate.projectName)
    }

    private fun snapshotJson(snapshot: RegistrationSnapshot): JsonObject = buildJsonObject {
        put("project_item_registered", snapshot.projectItemRegistered)
        put("cpp_psi_source_registered", snapshot.cppPsiSourceRegistered)
        put("primary_cpp_psi_available", snapshot.primaryCppPsiAvailable)
        put("provides_code_model", snapshot.providesCodeModel)
    }

    private fun diagnosticsJson(diagnostics: List<RegistrationDiagnostic>): JsonArray =
        JsonArray(diagnostics.map { diagnostic ->
            buildJsonObject {
                put("code", diagnostic.code)
                put("message", diagnostic.message)
            }
        })

    private fun remainingMillis(deadlineNanos: Long): Long? {
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0L) return null
        return ((remainingNanos + 999_999L) / 1_000_000L).coerceAtLeast(1L)
    }

    private data class ParentCandidate(
        val entity: ProjectModelEntity,
        val projectEntity: ProjectModelEntity,
        val directory: Path,
    ) {
        val entityKind: String
            get() = if (entity.isProject()) "project" else "project_folder"

        val projectName: String
            get() = projectEntity.name
    }

    private data class RegistrationSnapshot(
        val projectItemRegistered: Boolean,
        val cppPsiSourceRegistered: Boolean,
        val primaryCppPsiAvailable: Boolean,
        val providesCodeModel: Boolean,
    ) {
        val semanticReady: Boolean
            get() = projectItemRegistered &&
                cppPsiSourceRegistered &&
                primaryCppPsiAvailable &&
                providesCodeModel
    }

    private data class RegistrationDiagnostic(val code: String, val message: String)

    private data class AddCallCompletion(val result: RdAddItemDataResult?)

    private companion object {
        const val VerificationScope = "project_item_and_primary_cpp_psi"
    }
}
