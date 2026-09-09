// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

package model.rider

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator
import com.jetbrains.rider.model.nova.ide.SolutionModel

@Suppress("unused")
object BathurReSharperMcpToolsetModel : Ext(SolutionModel.Solution) {
    init {
        setting(CSharp50Generator.Namespace, "Bathur.ReSharperMcpToolset.Protocol")
        setting(Kotlin11Generator.Namespace, "local.bathur.resharper.mcp.toolset")

        val sourcePosition = structdef("CppSourcePosition") {
            field("filePath", string)
            field("line", int)
            field("column", int)
        }

        val diagnostic = structdef("CppSemanticDiagnostic") {
            field("code", string)
            field("message", string)
        }

        val pageInfo = structdef("CppPageInfo") {
            field("offset", int)
            field("returnedCount", int)
            field("totalMappedCount", int)
            field("hasTotalMappedCount", bool)
            field("hasMore", bool)
            field("nextOffset", int)
            field("skippedCount", int)
        }

        val symbolSummary = structdef("CppSymbolSummary") {
            field("name", string)
            field("qualifiedName", string)
            field("kind", string)
            field("signature", string)
            field("containingType", string)
            field("ueModule", string)
            field("navigation", sourcePosition)
            field("hasNavigation", bool)
            field("declarationCount", int)
            field("definitionCount", int)
        }

        val inspectedSymbol = structdef("CppInspectedSymbol") {
            field("summary", symbolSummary)
            field("declarations", array(sourcePosition))
            field("definitions", array(sourcePosition))
            field("accessibility", string)
            field("isVirtual", string)
            field("isPureVirtual", string)
            field("isFinal", string)
        }

        val targetRequest = structdef("CppTargetRequest") {
            field("filePath", string)
            field("line", int)
            field("column", int)
        }

        val pagedTargetRequest = structdef("CppPagedTargetRequest") {
            field("filePath", string)
            field("line", int)
            field("column", int)
            field("offset", int)
            field("maxResults", int)
        }

        val searchSymbolsRequest = structdef("SearchSymbolsRequest") {
            field("name", string)
            field("kinds", array(string))
            field("offset", int)
            field("maxResults", int)
        }

        val fileOutlineRequest = structdef("CppFileOutlineRequest") {
            field("filePath", string)
            field("offset", int)
            field("maxResults", int)
        }

        val fileSemanticStateRequest = structdef("CppFileSemanticStateRequest") {
            field("filePath", string)
        }

        val diagnosticsRequest = structdef("CppDiagnosticsRequest") {
            field("filePath", string)
            field("hasPosition", bool)
            field("line", int)
            field("column", int)
            field("minSeverity", string)
            field("offset", int)
            field("maxResults", int)
        }

        val reference = structdef("CppReferenceResult") {
            field("location", sourcePosition)
            field("context", string)
            field("usageKind", string)
        }

        val baseType = structdef("CppBaseTypeResult") {
            field("symbol", symbolSummary)
            field("accessibility", string)
            field("isVirtual", string)
            field("isImplicit", string)
            field("isPackExpansion", string)
        }

        val relatedSymbol = structdef("CppRelatedSymbolResult") {
            field("symbol", symbolSummary)
            field("relation", string)
        }

        val fileSymbol = structdef("CppFileSymbolOccurrence") {
            field("outlineIndex", int)
            field("parentOutlineIndex", int)
            field("depth", int)
            field("name", string)
            field("qualifiedName", string)
            field("kind", string)
            field("signature", string)
            field("containingType", string)
            field("declarationRole", string)
            field("location", sourcePosition)
        }

        val diagnosticsSourceFile = structdef("CppDiagnosticsSourceFile") {
            field("isGeneratedFile", bool)
            field("isNonUserFile", bool)
            field("providesCodeModel", bool)
        }

        val diagnosticRange = structdef("CppDiagnosticRange") {
            field("filePath", string)
            field("startLine", int)
            field("startColumn", int)
            field("endLine", int)
            field("endColumn", int)
            field("endExclusive", bool)
        }

        val diagnosticFinding = structdef("CppDiagnosticFinding") {
            field("severity", string)
            field("inspectionId", string)
            field("compilerIds", array(string))
            field("highlightingType", string)
            field("message", string)
            field("stage", string)
            field("range", diagnosticRange)
        }

        val diagnosticsDaemon = structdef("CppDiagnosticsDaemon") {
            field("state", string)
            field("completionBasis", string)
            field("processKind", string)
            field("analysisScope", string)
            field("stagePolicy", string)
            field("contributingStages", array(string))
            field("rawHighlightingCount", int)
        }

        val diagnosticsMetadata = structdef("CppDiagnosticsMetadata") {
            field("rawHighlightingCount", int)
            field("belowSeverityCount", int)
            field("outsidePositionCount", int)
            field("invalidRangeCount", int)
            field("unreadableSeverityCount", int)
            field("unreadableMetadataCount", int)
            field("unmappedRangeCount", int)
            field("matchedMappedCount", int)
            field("hasMatchedMappedCount", bool)
        }

        val inspectSymbolResponse = structdef("InspectSymbolResponse") {
            field("status", string)
            field("symbols", array(inspectedSymbol))
            field("searchScope", string)
            field("diagnostics", array(diagnostic))
        }

        val searchSymbolsResponse = structdef("SearchSymbolsResponse") {
            field("status", string)
            field("symbols", array(symbolSummary))
            field("page", pageInfo)
            field("searchScope", string)
            field("diagnostics", array(diagnostic))
        }

        val response = structdef("FindReferencesResponse") {
            field("status", string)
            field("targets", array(symbolSummary))
            field("references", array(reference))
            field("page", pageInfo)
            field("searchScope", string)
            field("diagnostics", array(diagnostic))
        }

        val baseTypesResponse = structdef("BaseTypesResponse") {
            field("status", string)
            field("targets", array(symbolSummary))
            field("baseTypes", array(baseType))
            field("page", pageInfo)
            field("searchScope", string)
            field("diagnostics", array(diagnostic))
        }

        val relatedSymbolsResponse = structdef("RelatedSymbolsResponse") {
            field("status", string)
            field("targets", array(symbolSummary))
            field("symbols", array(relatedSymbol))
            field("page", pageInfo)
            field("searchScope", string)
            field("diagnostics", array(diagnostic))
        }

        val listSymbolsInFileResponse = structdef("ListSymbolsInFileResponse") {
            field("status", string)
            field("filePath", string)
            field("ueModule", string)
            field("psiContext", string)
            field("symbols", array(fileSymbol))
            field("page", pageInfo)
            field("diagnostics", array(diagnostic))
        }

        val fileSemanticStateResponse = structdef("CppFileSemanticStateResponse") {
            field("status", string)
            field("filePath", string)
            field("cppPsiSourceRegistered", bool)
            field("primaryCppPsiAvailable", bool)
            field("providesCodeModel", bool)
            field("primaryPsiLanguage", string)
            field("diagnostics", array(diagnostic))
        }

        val getDiagnosticsResponse = structdef("GetDiagnosticsResponse") {
            field("status", string)
            field("filePath", string)
            field("psiContext", string)
            field("primaryPsiLanguage", string)
            field("indexEvidence", string)
            field("hasSourceFile", bool)
            field("sourceFile", diagnosticsSourceFile)
            field("hasDaemon", bool)
            field("daemon", diagnosticsDaemon)
            field("findings", array(diagnosticFinding))
            field("page", pageInfo)
            field("findingsMetadata", diagnosticsMetadata)
            field("diagnostics", array(diagnostic))
        }

        call("inspectSymbol", targetRequest, inspectSymbolResponse).async
        call("searchSymbols", searchSymbolsRequest, searchSymbolsResponse).async
        call("listSymbolsInFile", fileOutlineRequest, listSymbolsInFileResponse).async
        call("getCppFileSemanticState", fileSemanticStateRequest, fileSemanticStateResponse).async
        call("getDiagnostics", diagnosticsRequest, getDiagnosticsResponse).async
        call("findReferences", pagedTargetRequest, response).async
        call("getDirectBaseTypes", pagedTargetRequest, baseTypesResponse).async
        call("findDerivedTypes", pagedTargetRequest, relatedSymbolsResponse).async
        call("getDirectOverriddenMembers", pagedTargetRequest, relatedSymbolsResponse).async
        call("findOverridingMembers", pagedTargetRequest, relatedSymbolsResponse).async
    }
}
