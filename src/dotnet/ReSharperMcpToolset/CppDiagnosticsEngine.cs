// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using Bathur.ReSharperMcpToolset.Protocol;
using JetBrains.Application.Settings;
using JetBrains.DocumentManagers;
using JetBrains.DocumentModel;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Feature.Services.Daemon;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Psi.Cpp.Caches;
using JetBrains.ReSharper.Psi.Cpp.Language;
using JetBrains.ReSharper.Psi.Cpp.Tree;
using JetBrains.ReSharper.Psi.Dependencies;
using JetBrains.ReSharper.Psi.Files;
using JetBrains.Util;
using TypedDocColumn = JetBrains.Util.dataStructures.TypedIntrinsics.Int32<JetBrains.DocumentModel.DocColumn>;
using TypedDocLine = JetBrains.Util.dataStructures.TypedIntrinsics.Int32<JetBrains.DocumentModel.DocLine>;

namespace Bathur.ReSharperMcpToolset
{
    internal sealed class CppDiagnosticsEngine
    {
        private const string PrimaryCppPsiContext = "rider_primary_cpp_psi";
        private const string PrimaryCppIndexEvidence = "psi_source_registered_primary_cpp_available";
        private const string StagePolicy = "rider_visible_document_default";
        private static int _nextDiagnosticsOperationId;

        private readonly ISolution _solution;
        private readonly DocumentManager _documentManager;
        private readonly CppGlobalSymbolCache _globalSymbolCache;
        private readonly HighlightingSettingsManager _highlightingSettingsManager;
        private readonly ILogger _logger;

        public CppDiagnosticsEngine(
            ISolution solution,
            DocumentManager documentManager,
            CppGlobalSymbolCache globalSymbolCache,
            ILogger logger)
        {
            _solution = solution;
            _documentManager = documentManager;
            _globalSymbolCache = globalSymbolCache;
            _highlightingSettingsManager = solution.GetComponent<HighlightingSettingsManager>();
            _logger = logger;
        }

        public GetDiagnosticsResponse GetDiagnostics(
            Lifetime requestLifetime,
            CppDiagnosticsRequest request)
        {
            var stage = "validate request";
            var operationId = Interlocked.Increment(ref _nextDiagnosticsOperationId);
            try
            {
                requestLifetime.ThrowIfNotAlive();
                Severity minimumSeverity;
                if (!TryParseSeverity(request.MinSeverity, out minimumSeverity))
                {
                    return Failure(
                        "unsupported",
                        request.FilePath,
                        "invalid_min_severity",
                        "min_severity must be one of error, warning, suggestion, hint, or info",
                        request.Offset);
                }

                if (request.Offset < 0 || request.MaxResults < 1 || request.MaxResults > 200)
                {
                    return Failure(
                        "not_found",
                        request.FilePath,
                        "invalid_paging",
                        "offset must be zero or greater and max_results must be between 1 and 200",
                        request.Offset);
                }

                if (request.HasPosition && (request.Line < 1 || request.Column < 1))
                {
                    return Failure(
                        "not_found",
                        request.FilePath,
                        "invalid_position",
                        "line and column must both be 1-based positive integers",
                        request.Offset);
                }

                stage = "resolve C++ source file";
                var sourceResolution = ResolveSourceFile(request.FilePath);
                if (sourceResolution.Status != "ok")
                {
                    return Failure(
                        sourceResolution.Status,
                        sourceResolution.PhysicalPath,
                        sourceResolution.Diagnostic.Code,
                        sourceResolution.Diagnostic.Message,
                        request.Offset);
                }

                var sourceFile = sourceResolution.SourceFile;
                var sourceProperties = sourceFile.Properties;
                var sourceMetadata = new CppDiagnosticsSourceFile(
                    sourceProperties.IsGeneratedFile,
                    sourceProperties.IsNonUserFile,
                    sourceProperties.ProvidesCodeModel);

                requestLifetime.ThrowIfNotAlive();
                stage = "get primary PSI file";
                var primaryPsiFile = sourceFile.GetPrimaryPsiFile();
                if (primaryPsiFile == null || !primaryPsiFile.IsValid())
                {
                    return FailureWithSource(
                        "not_indexed",
                        sourceResolution.PhysicalPath,
                        string.Empty,
                        "psi_source_registered",
                        sourceMetadata,
                        "primary_psi_file_not_available",
                        "Semantic data is not available for this file yet.",
                        request.Offset);
                }

                var primaryPsiLanguage = primaryPsiFile.Language.ToString();
                var cppFile = primaryPsiFile as CppFile;
                if (cppFile == null || !sourceProperties.ProvidesCodeModel)
                {
                    return FailureWithSource(
                        "unsupported",
                        sourceResolution.PhysicalPath,
                        primaryPsiLanguage,
                        "psi_source_registered_primary_psi_available",
                        sourceMetadata,
                        "cpp_daemon_unavailable",
                        "C++ analysis is not available for this file.",
                        request.Offset);
                }

                DocumentOffset? positionOffset = null;
                if (request.HasPosition)
                {
                    stage = "map requested position";
                    CppSemanticDiagnostic positionDiagnostic;
                    if (!TryMapPosition(
                            sourceFile.Document,
                            request.Line,
                            request.Column,
                            out positionOffset,
                            out positionDiagnostic))
                    {
                        return FailureWithSource(
                            "not_found",
                            sourceResolution.PhysicalPath,
                            primaryPsiLanguage,
                            PrimaryCppIndexEvidence,
                            sourceMetadata,
                            positionDiagnostic.Code,
                            positionDiagnostic.Message,
                            request.Offset);
                    }
                }

                stage = "read daemon settings";
                var analysisScope = _highlightingSettingsManager.GetAnalysisEnabled(sourceFile);
                if (analysisScope == AnalysisScope.OFF)
                {
                    return FailureWithSource(
                        "unsupported",
                        sourceResolution.PhysicalPath,
                        primaryPsiLanguage,
                        PrimaryCppIndexEvidence,
                        sourceMetadata,
                        "cpp_analysis_disabled",
                        "Rider code analysis is disabled for the requested file.",
                        request.Offset);
                }

                var settings = sourceFile.GetSettingsStoreWithEditorConfig(_solution);
                requestLifetime.ThrowIfNotAlive();

                stage = "run C++ daemon";
                _logger.Info(
                    "ReSharper MCP diagnostics #{0}: starting VISIBLE_DOCUMENT daemon for {1} with Rider default stages.",
                    operationId,
                    sourceResolution.PhysicalPath);
                var process = new CollectingDaemonProcess(sourceFile, settings, requestLifetime);
                process.RunHeadless();
                requestLifetime.ThrowIfNotAlive();
                _logger.Info(
                    "ReSharper MCP diagnostics #{0}: daemon returned with {1} raw highlighting(s) from {2} contributing stage(s).",
                    operationId,
                    process.RawHighlightingCount,
                    process.ContributingStages.Length);

                stage = "map daemon findings";
                var rawHighlightingCount = process.RawHighlightingCount;
                var belowSeverityCount = 0;
                var outsidePositionCount = 0;
                var invalidRangeCount = 0;
                var unreadableSeverityCount = 0;
                var unreadableMetadataCount = 0;
                var metadataSkippedCount = 0;
                var unmappedRangeCount = 0;
                var mappedFindings = new List<RankedFinding>();

                foreach (var collected in process.Collected)
                {
                    requestLifetime.ThrowIfNotAlive();
                    var info = collected.Info;
                    var highlighting = info?.Highlighting;
                    if (info == null || highlighting == null)
                    {
                        unreadableMetadataCount++;
                        metadataSkippedCount++;
                        continue;
                    }

                    var range = info.Range;
                    bool highlightingIsValid;
                    try
                    {
                        highlightingIsValid = highlighting.IsValid();
                    }
                    catch
                    {
                        invalidRangeCount++;
                        continue;
                    }

                    if (!highlightingIsValid || !range.IsValid() || !range.IsNormalized || range.Document == null)
                    {
                        invalidRangeCount++;
                        continue;
                    }

                    Severity severity;
                    try
                    {
                        severity = info.GetSeverity(_highlightingSettingsManager, sourceFile, settings);
                    }
                    catch
                    {
                        unreadableSeverityCount++;
                        continue;
                    }

                    if ((int)severity < (int)minimumSeverity)
                    {
                        belowSeverityCount++;
                        continue;
                    }

                    if (positionOffset.HasValue &&
                        (!ReferenceEquals(range.Document, positionOffset.Value.Document) ||
                         !range.Contains(positionOffset.Value)))
                    {
                        outsidePositionCount++;
                        continue;
                    }

                    string physicalPath;
                    if (ReferenceEquals(range.Document, sourceFile.Document))
                    {
                        physicalPath = sourceResolution.PhysicalPath;
                    }
                    else
                    {
                        var mappedPath = _documentManager.TryGetDocumentFilePath(range.Document);
                        if (mappedPath == null || mappedPath.IsEmpty)
                        {
                            unmappedRangeCount++;
                            continue;
                        }

                        physicalPath = mappedPath.ToString();
                    }

                    var metadataUnreadable = false;
                    var inspectionId = string.Empty;
                    try
                    {
                        var attribute = _highlightingSettingsManager.GetHighlightingAttribute(highlighting);
                        if (attribute != null)
                        {
                            inspectionId = highlighting.GetConfigurableSeverityId(attribute, false) ?? string.Empty;
                        }
                    }
                    catch
                    {
                        metadataUnreadable = true;
                    }

                    string[] compilerIds;
                    try
                    {
                        compilerIds = info
                            .GetCompilerIds(_highlightingSettingsManager, sourceFile, CppLanguage.Instance)
                            .Where(id => !string.IsNullOrWhiteSpace(id))
                            .Distinct(StringComparer.Ordinal)
                            .OrderBy(id => id, StringComparer.Ordinal)
                            .ToArray();
                    }
                    catch
                    {
                        compilerIds = Array.Empty<string>();
                        metadataUnreadable = true;
                    }

                    var message = string.Empty;
                    try
                    {
                        message = highlighting.ToolTip ?? string.Empty;
                    }
                    catch
                    {
                        metadataUnreadable = true;
                    }

                    if (message.Length == 0)
                    {
                        try
                        {
                            message = highlighting.ErrorStripeToolTip ?? string.Empty;
                        }
                        catch
                        {
                            metadataUnreadable = true;
                        }
                    }

                    if (metadataUnreadable)
                    {
                        unreadableMetadataCount++;
                    }

                    var start = range.StartOffset.ToDocumentCoords();
                    var end = range.EndOffset.ToDocumentCoords();
                    var findingRange = new CppDiagnosticRange(
                        physicalPath,
                        (int)start.Line + 1,
                        (int)start.Column + 1,
                        (int)end.Line + 1,
                        (int)end.Column + 1,
                        true);
                    var finding = new CppDiagnosticFinding(
                        SeverityToString(severity),
                        inspectionId,
                        compilerIds,
                        highlighting.GetType().FullName ?? highlighting.GetType().Name,
                        message,
                        collected.Stage,
                        findingRange);
                    mappedFindings.Add(new RankedFinding(finding, severity));
                }

                var orderedFindings = mappedFindings
                    .OrderBy(item => item.Finding.Range.FilePath, StringComparer.OrdinalIgnoreCase)
                    .ThenBy(item => item.Finding.Range.StartLine)
                    .ThenBy(item => item.Finding.Range.StartColumn)
                    .ThenBy(item => item.Finding.Range.EndLine)
                    .ThenBy(item => item.Finding.Range.EndColumn)
                    .ThenByDescending(item => (int)item.Severity)
                    .ThenBy(item => item.Finding.InspectionId, StringComparer.Ordinal)
                    .ThenBy(item => item.Finding.HighlightingType, StringComparer.Ordinal)
                    .ThenBy(item => item.Finding.Stage, StringComparer.Ordinal)
                    .Select(item => item.Finding)
                    .ToArray();
                var pageItems = orderedFindings.Skip(request.Offset).Take(request.MaxResults).ToArray();
                var hasMore = request.Offset + pageItems.Length < orderedFindings.Length;
                var hasReliableMappedCount = invalidRangeCount == 0 &&
                                             unreadableSeverityCount == 0 &&
                                             metadataSkippedCount == 0 &&
                                             unmappedRangeCount == 0;
                var skippedCount = invalidRangeCount + unreadableSeverityCount +
                                   metadataSkippedCount + unmappedRangeCount;
                var responseDiagnostics = new List<CppSemanticDiagnostic>();
                if (invalidRangeCount > 0)
                {
                    responseDiagnostics.Add(Diagnostic(
                        "invalid_highlightings",
                        "Some findings were omitted because they or their source ranges are invalid."));
                }

                if (unreadableSeverityCount > 0 || unreadableMetadataCount > 0)
                {
                    responseDiagnostics.Add(Diagnostic(
                        "unreadable_highlightings",
                        "Some findings were omitted or have incomplete metadata; see findings_metadata."));
                }

                if (unmappedRangeCount > 0)
                {
                    responseDiagnostics.Add(Diagnostic(
                        "unmapped_highlightings",
                        "Some findings were omitted because their source locations could not be mapped."));
                }

                var isPartial = invalidRangeCount > 0 || unreadableSeverityCount > 0 ||
                                unreadableMetadataCount > 0 || unmappedRangeCount > 0;
                var normalizedAnalysisScope = analysisScope.ToString().ToLowerInvariant();
                _logger.Info(
                    "ReSharper MCP diagnostics #{0}: mapped {1} finding(s), below severity {2}, outside position {3}, invalid {4}, unreadable severity {5}, unreadable metadata {6}, unmapped {7}.",
                    operationId,
                    orderedFindings.Length,
                    belowSeverityCount,
                    outsidePositionCount,
                    invalidRangeCount,
                    unreadableSeverityCount,
                    unreadableMetadataCount,
                    unmappedRangeCount);
                return new GetDiagnosticsResponse(
                    isPartial ? "partial" : "ok",
                    sourceResolution.PhysicalPath,
                    PrimaryCppPsiContext,
                    primaryPsiLanguage,
                    PrimaryCppIndexEvidence,
                    true,
                    sourceMetadata,
                    true,
                    new CppDiagnosticsDaemon(
                        "completed",
                        "do_highlighting_returned",
                        "visible_document",
                        normalizedAnalysisScope,
                        StagePolicy,
                        process.ContributingStages,
                        rawHighlightingCount),
                    pageItems,
                    new CppPageInfo(
                        request.Offset,
                        pageItems.Length,
                        orderedFindings.Length,
                        hasReliableMappedCount,
                        hasMore,
                        hasMore ? request.Offset + pageItems.Length : -1,
                        skippedCount),
                    new CppDiagnosticsMetadata(
                        rawHighlightingCount,
                        belowSeverityCount,
                        outsidePositionCount,
                        invalidRangeCount,
                        unreadableSeverityCount,
                        unreadableMetadataCount,
                        unmappedRangeCount,
                        orderedFindings.Length,
                        hasReliableMappedCount),
                    responseDiagnostics.ToArray());
            }
            catch (OperationCanceledException)
            {
                _logger.Info(
                    "ReSharper MCP diagnostics #{0}: request cancelled during {1}.",
                    operationId,
                    stage);
                throw;
            }
            catch (Exception exception)
            {
                throw new InvalidOperationException(
                    $"Rider C++ diagnostics failed during {stage}.",
                    exception);
            }
        }

        private SourceFileResolution ResolveSourceFile(string filePath)
        {
            var virtualPath = VirtualFileSystemPath.Parse(
                filePath,
                _solution.GetInteractionContext(),
                FileSystemPathInternStrategy.DO_NOT_INTERN);
            if (virtualPath.IsEmpty || !virtualPath.ExistsFile)
            {
                return SourceFileResolution.Failure(
                    "not_found",
                    filePath,
                    Diagnostic("file_not_found", "The requested file does not exist: " + filePath));
            }

            var cppModule = _globalSymbolCache.CppModule;
            var sourceFile = cppModule.LookupFileInAnyProject(virtualPath) ?? cppModule.LookupFile(virtualPath);
            if (sourceFile == null || !sourceFile.IsValid())
            {
                return SourceFileResolution.Failure(
                    "not_indexed",
                    virtualPath.ToString(),
                    Diagnostic(
                        "psi_source_not_registered",
                        "This file is not registered in Rider's C++ project model."));
            }

            return new SourceFileResolution(
                "ok",
                virtualPath.ToString(),
                sourceFile,
                Diagnostic(string.Empty, string.Empty));
        }

        private static bool TryMapPosition(
            IDocument document,
            int line,
            int column,
            out DocumentOffset? offset,
            out CppSemanticDiagnostic diagnostic)
        {
            offset = null;
            var lineCount = (int)document.GetLineCount();
            if (line > lineCount)
            {
                diagnostic = Diagnostic(
                    "position_out_of_range",
                    $"Line {line} is outside the document, which has {lineCount} lines.");
                return false;
            }

            var lineIndex = (TypedDocLine)(line - 1);
            var lineLength = (int)document.GetLineLength(lineIndex);
            if (column > lineLength + 1)
            {
                diagnostic = Diagnostic(
                    "position_out_of_range",
                    $"Column {column} is outside line {line}, whose maximum position is {lineLength + 1}.");
                return false;
            }

            var mappedOffset = document.GetOffsetByCoordsSafe(
                new DocumentCoords(lineIndex, (TypedDocColumn)(column - 1)));
            if (!mappedOffset.HasValue)
            {
                diagnostic = Diagnostic(
                    "position_not_mapped",
                    "The source position could not be located in the current document.");
                return false;
            }

            offset = new DocumentOffset(document, mappedOffset.Value);
            diagnostic = Diagnostic(string.Empty, string.Empty);
            return true;
        }

        private static bool TryParseSeverity(string value, out Severity severity)
        {
            switch ((value ?? string.Empty).Trim().ToLowerInvariant())
            {
                case "error": severity = Severity.ERROR; return true;
                case "warning": severity = Severity.WARNING; return true;
                case "suggestion": severity = Severity.SUGGESTION; return true;
                case "hint": severity = Severity.HINT; return true;
                case "info": severity = Severity.INFO; return true;
                default: severity = Severity.INVALID_SEVERITY; return false;
            }
        }

        private static string SeverityToString(Severity severity)
        {
            switch (severity)
            {
                case Severity.ERROR: return "error";
                case Severity.WARNING: return "warning";
                case Severity.SUGGESTION: return "suggestion";
                case Severity.HINT: return "hint";
                case Severity.INFO: return "info";
                default: return "unknown";
            }
        }

        private static GetDiagnosticsResponse Failure(
            string status,
            string filePath,
            string code,
            string message,
            int offset)
        {
            return new GetDiagnosticsResponse(
                status,
                filePath ?? string.Empty,
                PrimaryCppPsiContext,
                string.Empty,
                string.Empty,
                false,
                EmptySourceFile(),
                false,
                EmptyDaemon(),
                Array.Empty<CppDiagnosticFinding>(),
                EmptyPage(offset),
                EmptyMetadata(),
                new[] { Diagnostic(code, message) });
        }

        private static GetDiagnosticsResponse FailureWithSource(
            string status,
            string filePath,
            string primaryPsiLanguage,
            string indexEvidence,
            CppDiagnosticsSourceFile sourceFile,
            string code,
            string message,
            int offset)
        {
            return new GetDiagnosticsResponse(
                status,
                filePath ?? string.Empty,
                PrimaryCppPsiContext,
                primaryPsiLanguage ?? string.Empty,
                indexEvidence ?? string.Empty,
                true,
                sourceFile,
                false,
                EmptyDaemon(),
                Array.Empty<CppDiagnosticFinding>(),
                EmptyPage(offset),
                EmptyMetadata(),
                new[] { Diagnostic(code, message) });
        }

        private static CppDiagnosticsSourceFile EmptySourceFile()
        {
            return new CppDiagnosticsSourceFile(false, false, false);
        }

        private static CppDiagnosticsDaemon EmptyDaemon()
        {
            return new CppDiagnosticsDaemon(
                string.Empty,
                string.Empty,
                string.Empty,
                string.Empty,
                string.Empty,
                Array.Empty<string>(),
                0);
        }

        private static CppDiagnosticsMetadata EmptyMetadata()
        {
            return new CppDiagnosticsMetadata(0, 0, 0, 0, 0, 0, 0, 0, false);
        }

        private static CppPageInfo EmptyPage(int offset)
        {
            return new CppPageInfo(Math.Max(0, offset), 0, 0, false, false, -1, 0);
        }

        private static CppSemanticDiagnostic Diagnostic(string code, string message)
        {
            return new CppSemanticDiagnostic(code, message);
        }

        private sealed class RankedFinding
        {
            public RankedFinding(CppDiagnosticFinding finding, Severity severity)
            {
                Finding = finding;
                Severity = severity;
            }

            public CppDiagnosticFinding Finding { get; }
            public Severity Severity { get; }
        }

        private sealed class SourceFileResolution
        {
            public SourceFileResolution(
                string status,
                string physicalPath,
                IPsiSourceFile sourceFile,
                CppSemanticDiagnostic diagnostic)
            {
                Status = status;
                PhysicalPath = physicalPath;
                SourceFile = sourceFile;
                Diagnostic = diagnostic;
            }

            public string Status { get; }
            public string PhysicalPath { get; }
            public IPsiSourceFile SourceFile { get; }
            public CppSemanticDiagnostic Diagnostic { get; }

            public static SourceFileResolution Failure(
                string status,
                string physicalPath,
                CppSemanticDiagnostic diagnostic)
            {
                return new SourceFileResolution(status, physicalPath ?? string.Empty, null, diagnostic);
            }
        }

        private sealed class CollectedHighlighting
        {
            public CollectedHighlighting(HighlightingInfo info, string stage)
            {
                Info = info;
                Stage = stage;
            }

            public HighlightingInfo Info { get; }
            public string Stage { get; }
        }

        private sealed class CollectingDaemonProcess : DaemonProcessBase
        {
            private readonly Lifetime _requestLifetime;
            private readonly ConcurrentQueue<CollectedHighlighting> _collected =
                new ConcurrentQueue<CollectedHighlighting>();
            private readonly ConcurrentDictionary<string, byte> _contributingStages =
                new ConcurrentDictionary<string, byte>(StringComparer.Ordinal);
            private int _rawHighlightingCount;

            public CollectingDaemonProcess(
                IPsiSourceFile sourceFile,
                IContextBoundSettingsStore settings,
                Lifetime requestLifetime)
                : base(sourceFile, sourceFile.Document, settings)
            {
                _requestLifetime = requestLifetime;
            }

            public IEnumerable<CollectedHighlighting> Collected => _collected;

            public int RawHighlightingCount => _rawHighlightingCount;

            public string[] ContributingStages => _contributingStages.Keys
                .OrderBy(name => name, StringComparer.Ordinal)
                .ToArray();

            public void RunHeadless()
            {
                DoHighlighting(DaemonProcessKind.VISIBLE_DOCUMENT, Commit);
            }

            private void Commit(DaemonCommitContext context)
            {
                _requestLifetime.ThrowIfNotAlive();
                var publicContext = (IDaemonCommitContext)context;
                var highlightings = publicContext.HighlightingsToAdd;
                if (highlightings == null || highlightings.Count == 0)
                {
                    return;
                }

                var stageType = publicContext.StageId.Item1;
                var stageName = stageType?.Name ?? string.Empty;
                _contributingStages.TryAdd(stageName, 0);
                Interlocked.Add(ref _rawHighlightingCount, highlightings.Count);
                foreach (var highlighting in highlightings)
                {
                    _requestLifetime.ThrowIfNotAlive();
                    _collected.Enqueue(new CollectedHighlighting(highlighting, stageName));
                }
            }

            public override bool FullRehighlightingRequired => true;

            public override bool IsRangeInvalidated(DocumentRange range) => true;

            public override bool InterruptFlag => _requestLifetime.IsNotAlive;

            protected override bool ShouldNotifySwea(IPsiSourceFile sourceFile) => false;

            protected override void AnalysisStageCompleted(
                IPsiSourceFile sourceFile,
                IDaemonStage stage,
                byte layer,
                List<HighlightingInfo> stageHighlightings,
                bool stageFullRehighlight,
                List<DocumentRange> stageRanges,
                DaemonProcessKind processKind,
                IContextBoundSettingsStore settingsStore)
            {
            }

            protected override void FilePartlyReanalyzed(
                IPsiSourceFile sourceFile,
                DaemonProcessBase daemonProcessBase,
                DaemonProcessKind processKind)
            {
            }

            protected override void AnalysisCompleted(
                IPsiSourceFile sourceFile,
                DaemonProcessBase daemonProcessBase,
                DependencySet dependencies,
                bool analysisSupported,
                DaemonProcessKind processKind)
            {
            }
        }
    }
}
