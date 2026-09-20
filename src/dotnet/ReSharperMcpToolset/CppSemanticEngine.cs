// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Bathur.ReSharperMcpToolset.Protocol;
using JetBrains.Application.Progress;
using JetBrains.DocumentManagers;
using JetBrains.DocumentModel;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Feature.Services.CodeStructure;
using JetBrains.ReSharper.Feature.Services.Cpp;
using JetBrains.ReSharper.Feature.Services.Cpp.CodeStructure;
using JetBrains.ReSharper.Feature.Services.Cpp.DeclaredElements;
using JetBrains.ReSharper.Feature.Services.Cpp.Navigation.Goto;
using JetBrains.ReSharper.Feature.Services.Cpp.Occurrences;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4.UEAsset.Search;
using JetBrains.ReSharper.Feature.Services.Occurrences;
using JetBrains.ReSharper.Feature.Services.Util;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Psi.Cpp.Caches;
using JetBrains.ReSharper.Psi.Cpp.Language;
using JetBrains.ReSharper.Psi.Cpp.Presentation;
using JetBrains.ReSharper.Psi.Cpp.Symbols;
using JetBrains.ReSharper.Psi.Cpp.Tree;
using JetBrains.ReSharper.Psi.Cpp.Types;
using JetBrains.ReSharper.Psi.Files;
using JetBrains.ReSharper.Psi.Impl.Search.Operations;
using JetBrains.ReSharper.Psi.Pointers;
using JetBrains.ReSharper.Psi.Resolve;
using JetBrains.ReSharper.Psi.Search;
using JetBrains.ReSharper.Psi.Tree;
using JetBrains.Util;
using TypedDocColumn = JetBrains.Util.dataStructures.TypedIntrinsics.Int32<JetBrains.DocumentModel.DocColumn>;
using TypedDocLine = JetBrains.Util.dataStructures.TypedIntrinsics.Int32<JetBrains.DocumentModel.DocLine>;

namespace Bathur.ReSharperMcpToolset
{
    internal sealed class FindReferencesExecution
    {
        public FindReferencesExecution(FindReferencesResponse immediateResponse)
        {
            ImmediateResponse = immediateResponse;
        }

        public FindReferencesExecution(
            int operationId,
            CppPagedTargetRequest request,
            IDeclaredElementPointer<IDeclaredElement>[] targetPointers,
            CppSemanticDiagnostic[] diagnostics,
            List<FindResult> results,
            Task completion)
        {
            OperationId = operationId;
            Request = request;
            TargetPointers = targetPointers;
            Diagnostics = diagnostics;
            Results = results;
            Completion = completion;
        }

        public FindReferencesResponse ImmediateResponse { get; }

        public int OperationId { get; }

        public CppPagedTargetRequest Request { get; }

        public IDeclaredElementPointer<IDeclaredElement>[] TargetPointers { get; }

        public CppSemanticDiagnostic[] Diagnostics { get; }

        public List<FindResult> Results { get; }

        public Task Completion { get; }
    }

    internal sealed class CppSemanticEngine
    {
        private const string SearchScope = "current_solution_and_rider_known_libraries";
        private const string PrimaryCppPsiContext = "rider_primary_cpp_psi";
        private const string TooManyCodeStructureElementsType =
            "JetBrains.ReSharper.Feature.Services.Cpp.CodeStructure.CppTooManyElementsCodeStructureElement";
        private const string CodeStructureQualifierElementType =
            "JetBrains.ReSharper.Feature.Services.Cpp.CodeStructure.CppCodeStructureQualifierElement";
        private static int _nextReferencesOperationId;

        private readonly ISolution _solution;
        private readonly IPsiServices _psiServices;
        private readonly DocumentManager _documentManager;
        private readonly IFinderOperationManager _finderOperationManager;
        private readonly ICppUE4ModuleNamesProvider _ue4ModuleNamesProvider;
        private readonly CppGlobalSymbolCache _globalSymbolCache;
        private readonly ILogger _logger;

        public CppSemanticEngine(
            ISolution solution,
            IPsiServices psiServices,
            DocumentManager documentManager,
            IFinderOperationManager finderOperationManager,
            ICppUE4ModuleNamesProvider ue4ModuleNamesProvider,
            CppGlobalSymbolCache globalSymbolCache,
            ILogger logger)
        {
            _solution = solution;
            _psiServices = psiServices;
            _documentManager = documentManager;
            _finderOperationManager = finderOperationManager;
            _ue4ModuleNamesProvider = ue4ModuleNamesProvider;
            _globalSymbolCache = globalSymbolCache;
            _logger = logger;
        }

        public CppFileSemanticStateResponse GetCppFileSemanticState(
            Lifetime requestLifetime,
            CppFileSemanticStateRequest request)
        {
            var stage = "validate request";
            try
            {
                requestLifetime.ThrowIfNotAlive();
                stage = "resolve C++ source file";
                var sourceResolution = ResolveSourceFile(request.FilePath);
                if (sourceResolution.Status != "ok")
                {
                    return new CppFileSemanticStateResponse(
                        sourceResolution.Status,
                        sourceResolution.PhysicalPath,
                        false,
                        false,
                        false,
                        string.Empty,
                        sourceResolution.Diagnostics);
                }

                requestLifetime.ThrowIfNotAlive();
                var sourceFile = sourceResolution.SourceFile;
                var sourceProperties = sourceFile.Properties;
                stage = "get primary C++ PSI file";
                var primaryPsiFile = sourceFile.GetPrimaryPsiFile();
                var primaryPsiAvailable = primaryPsiFile is CppFile && primaryPsiFile.IsValid();
                var primaryPsiLanguage = primaryPsiFile != null && primaryPsiFile.IsValid()
                    ? primaryPsiFile.Language.ToString()
                    : string.Empty;
                var providesCodeModel = sourceProperties.ProvidesCodeModel;

                if (!primaryPsiAvailable)
                {
                    return new CppFileSemanticStateResponse(
                        "not_indexed",
                        sourceResolution.PhysicalPath,
                        true,
                        false,
                        providesCodeModel,
                        primaryPsiLanguage,
                        new[]
                        {
                            Diagnostic(
                                "primary_cpp_psi_not_available",
                                "Rider has a registered C++ PSI source file, but no valid primary C++ PSI file is currently available.")
                        });
                }

                if (!providesCodeModel)
                {
                    return new CppFileSemanticStateResponse(
                        "unsupported",
                        sourceResolution.PhysicalPath,
                        true,
                        true,
                        false,
                        primaryPsiLanguage,
                        new[]
                        {
                            Diagnostic(
                                "cpp_code_model_not_available",
                                "The primary C++ PSI source does not currently provide a code model.")
                        });
                }

                return new CppFileSemanticStateResponse(
                    "ok",
                    sourceResolution.PhysicalPath,
                    true,
                    true,
                    true,
                    primaryPsiLanguage,
                    Array.Empty<CppSemanticDiagnostic>());
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception exception)
            {
                throw new InvalidOperationException(
                    $"Rider C++ file semantic state query failed during {stage}.",
                    exception);
            }
        }

        public InspectSymbolResponse InspectSymbol(Lifetime requestLifetime, CppTargetRequest request)
        {
            var stage = "validate request";
            try
            {
                requestLifetime.ThrowIfNotAlive();
                stage = "resolve target";
                var resolution = ResolveTarget(request.FilePath, request.Line, request.Column);
                requestLifetime.ThrowIfNotAlive();
                stage = "build inspected symbols";
                var inspected = resolution.Elements
                    .Select(BuildInspectedSymbol)
                    .OrderBy(item => NavigationPath(item.Summary), StringComparer.OrdinalIgnoreCase)
                    .ThenBy(item => NavigationLine(item.Summary))
                    .ThenBy(item => NavigationColumn(item.Summary))
                    .ThenBy(item => item.Summary.QualifiedName, StringComparer.Ordinal)
                    .ThenBy(item => item.Summary.Signature, StringComparer.Ordinal)
                    .ToArray();

                if (resolution.Status != "ok")
                {
                    return new InspectSymbolResponse(
                        resolution.Status,
                        inspected,
                        SearchScope,
                        resolution.Diagnostics.ToArray());
                }

                var skippedLocations = inspected.Sum(item =>
                    item.Summary.DeclarationCount + item.Summary.DefinitionCount == 0 ? 1 : 0);
                var diagnostics = new List<CppSemanticDiagnostic>(resolution.Diagnostics);
                if (skippedLocations > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "unmapped_symbol",
                        $"{skippedLocations} resolved symbol(s) have no navigable declaration or definition."));
                }

                return new InspectSymbolResponse(
                    skippedLocations > 0 ? "partial" : "ok",
                    inspected,
                    SearchScope,
                    diagnostics.ToArray());
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception exception)
            {
                throw new InvalidOperationException(
                    $"Rider C++ symbol inspection failed during {stage}.",
                    exception);
            }
        }

        public SearchSymbolsResponse SearchSymbols(Lifetime requestLifetime, SearchSymbolsRequest request)
        {
            var stage = "validate request";
            try
            {
                requestLifetime.ThrowIfNotAlive();
                if (string.IsNullOrWhiteSpace(request.Name))
                {
                    return SearchFailure("not_found", "invalid_name", "name must not be empty", request.Offset);
                }

                if (request.Offset < 0)
                {
                    return SearchFailure("not_found", "invalid_offset", "offset must be zero or greater", request.Offset);
                }

                if (request.MaxResults < 1 || request.MaxResults > 200)
                {
                    return SearchFailure(
                        "not_found",
                        "invalid_max_results",
                        "max_results must be between 1 and 200",
                        request.Offset);
                }

                var query = request.Name.Trim();
                var normalizedQuery = query.TrimStart(':');
                var separatorIndex = normalizedQuery.LastIndexOf("::", StringComparison.Ordinal);
                var shortName = separatorIndex >= 0
                    ? normalizedQuery.Substring(separatorIndex + 2)
                    : normalizedQuery;
                if (string.IsNullOrWhiteSpace(shortName))
                {
                    return SearchFailure(
                        "not_found",
                        "invalid_name",
                        "name must end with a C++ symbol name",
                        request.Offset);
                }

                var requestedKinds = new HashSet<string>(
                    request.Kinds
                        .Where(kind => !string.IsNullOrWhiteSpace(kind))
                        .Select(kind => kind.Trim().ToLowerInvariant()),
                    StringComparer.Ordinal);

                stage = "query C++ symbol index";
                if (_globalSymbolCache.IsEmpty())
                {
                    return SearchFailure(
                        "not_indexed",
                        "empty_cpp_index",
                        "The C++ symbol index is empty.",
                        request.Offset);
                }

                var indexedSymbols = Enumerable
                    .Where(
                        _globalSymbolCache.SymbolNameCache.GetSymbolsByShortNameWithReadLockHeld(shortName).ToEnumerable(),
                        (ICppSymbol symbol) => CppGotoSymbolUtil.IsValidSymbol(symbol))
                    .OfType<ICppParserSymbol>()
                    .ToArray();
                requestLifetime.ThrowIfNotAlive();

                stage = "resolve indexed symbols";
                var symbolToEntity = _globalSymbolCache.LinkageCache.FindEntitiesBySymbols(indexedSymbols, null);
                requestLifetime.ThrowIfNotAlive();

                stage = "format indexed symbols";
                var mapped = new List<CppSymbolSummary>();
                var unmappedCount = 0;
                var unreadableCount = 0;
                var unresolvedCount = 0;
                // Compare semantic entities and parser symbols, never incomplete declared-element wrappers.
                var seenEntities = new HashSet<ICppLinkageEntity>();
                var seenUnresolvedSymbols = new HashSet<ICppSymbol>(CppSymbolEqualityComparer.INSTANCE);
                foreach (var parserSymbol in indexedSymbols)
                {
                    try
                    {
                        requestLifetime.ThrowIfNotAlive();
                        ICppLinkageEntity entity;
                        ICppDeclaredElement element;
                        var hasIndexedIdentity = symbolToEntity.TryGetValue(parserSymbol, out entity) && entity != null;
                        if (hasIndexedIdentity)
                        {
                            if (!seenEntities.Add(entity)) continue;
                            element = new CppLinkageEntityDeclaredElement(_psiServices, entity);
                        }
                        else
                        {
                            // Retain the source occurrence for semantic position resolution, not lexical presentation.
                            if (!seenUnresolvedSymbols.Add(parserSymbol)) continue;
                            element = new CppParserSymbolDeclaredElement(_psiServices, parserSymbol);
                        }

                        if (!element.IsValid())
                        {
                            unreadableCount++;
                            continue;
                        }

                        if (!hasIndexedIdentity)
                        {
                            CppSourcePosition sourcePosition;
                            var hasSourcePosition = TryMapSymbol(parserSymbol, out sourcePosition);
                            requestLifetime.ThrowIfNotAlive();
                            if (!hasSourcePosition)
                            {
                                unmappedCount++;
                                continue;
                            }

                            // An indexed parser entry can be an elaborated type use. Its lexical container
                            // does not identify the type; use the same unique-target PSI resolution as inspect.
                            var resolution = ResolveTarget(sourcePosition.FilePath, sourcePosition.Line, sourcePosition.Column);
                            requestLifetime.ThrowIfNotAlive();
                            if (resolution.Status != "ok" || resolution.Elements.Length != 1)
                            {
                                unresolvedCount++;
                                continue;
                            }

                            element = resolution.Elements[0];
                        }

                        element = CanonicalizeDeclaredElement(element);
                        requestLifetime.ThrowIfNotAlive();
                        var recoveredLinkage = element as CppLinkageEntityDeclaredElement;
                        if (!hasIndexedIdentity)
                        {
                            if (recoveredLinkage == null || recoveredLinkage.GetLinkageEntity() == null)
                            {
                                unresolvedCount++;
                                continue;
                            }

                            if (!recoveredLinkage.IsValid())
                            {
                                unreadableCount++;
                                continue;
                            }

                            if (!seenEntities.Add(recoveredLinkage.GetLinkageEntity())) continue;
                        }

                        var matchesQuery = separatorIndex >= 0
                            ? string.Equals(
                                GetQualifiedName(element).TrimStart(':'),
                                normalizedQuery,
                                StringComparison.Ordinal)
                            : string.Equals(element.ShortName, shortName, StringComparison.Ordinal);
                        if (!matchesQuery)
                        {
                            continue;
                        }

                        var summary = BuildSymbolSummaryCore(element);
                        if (requestedKinds.Count > 0 && !requestedKinds.Contains(summary.Kind))
                        {
                            continue;
                        }

                        if (!summary.HasNavigation)
                        {
                            unmappedCount++;
                            continue;
                        }

                        mapped.Add(summary);
                    }
                    catch (OperationCanceledException)
                    {
                        throw;
                    }
                    catch
                    {
                        unreadableCount++;
                    }
                }

                var ordered = mapped
                    .OrderBy(NavigationPath, StringComparer.OrdinalIgnoreCase)
                    .ThenBy(NavigationLine)
                    .ThenBy(NavigationColumn)
                    .ThenBy(summary => summary.QualifiedName, StringComparer.Ordinal)
                    .ThenBy(summary => summary.Signature, StringComparer.Ordinal)
                    .ToArray();

                var pageItems = ordered.Skip(request.Offset).Take(request.MaxResults).ToArray();
                var hasMore = request.Offset + pageItems.Length < ordered.Length;
                var diagnostics = new List<CppSemanticDiagnostic>();
                if (unmappedCount > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "unmapped_symbols",
                        $"{unmappedCount} indexed symbol(s) have no navigable source location."));
                }

                if (unreadableCount > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "unreadable_indexed_symbols",
                        $"{unreadableCount} indexed symbol(s) could not be read."));
                }

                if (unresolvedCount > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "unresolved_indexed_symbols",
                        $"{unresolvedCount} indexed source occurrence(s) could not be resolved to a unique canonical target."));
                }

                var skippedCount = unmappedCount + unreadableCount + unresolvedCount;
                var complete = skippedCount == 0;

                var status = !complete ? "partial" : ordered.Length == 0 ? "not_found" : "ok";
                return new SearchSymbolsResponse(
                    status,
                    pageItems,
                    new CppPageInfo(
                        request.Offset,
                        pageItems.Length,
                        ordered.Length,
                        complete,
                        hasMore,
                        hasMore ? request.Offset + pageItems.Length : -1,
                        skippedCount),
                    SearchScope,
                    diagnostics.ToArray());
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception exception)
            {
                throw new InvalidOperationException(
                    $"Rider C++ exact symbol search failed during {stage}.",
                    exception);
            }
        }

        public ListSymbolsInFileResponse ListSymbolsInFile(
            Lifetime requestLifetime,
            CppFileOutlineRequest request)
        {
            var stage = "validate request";
            try
            {
                requestLifetime.ThrowIfNotAlive();
                if (request.Offset < 0 || request.MaxResults < 1 || request.MaxResults > 200)
                {
                    return FileOutlineFailure(
                        "not_found",
                        request.FilePath,
                        "invalid_paging",
                        "offset must be zero or greater and max_results must be between 1 and 200",
                        request.Offset);
                }

                stage = "resolve C++ source file";
                var sourceResolution = ResolveSourceFile(request.FilePath);
                if (sourceResolution.Status != "ok")
                {
                    return new ListSymbolsInFileResponse(
                        sourceResolution.Status,
                        sourceResolution.PhysicalPath,
                        string.Empty,
                        PrimaryCppPsiContext,
                        Array.Empty<CppFileSymbolOccurrence>(),
                        EmptyPage(request.Offset),
                        sourceResolution.Diagnostics);
                }

                requestLifetime.ThrowIfNotAlive();
                stage = "get primary C++ PSI file";
                var cppFile = sourceResolution.SourceFile.GetPrimaryPsiFile() as CppFile;
                if (cppFile == null || !cppFile.IsValid())
                {
                    return FileOutlineFailure(
                        "not_indexed",
                        sourceResolution.PhysicalPath,
                        "primary_psi_file_not_available",
                        "C++ semantic data is not available for this file yet.",
                        request.Offset);
                }

                stage = "build Rider C++ code structure";
                var provider = new CppCodeStructureProvider();
                var root = provider.Build(
                    cppFile,
                    new CodeStructureOptions
                    {
                        BuildInheritanceInformation = false,
                        ShowPreprocessorDirectives = false
                    });
                requestLifetime.ThrowIfNotAlive();

                stage = "map file symbol occurrences";
                var mapped = new List<CppFileSymbolOccurrence>();
                var unreadableCount = 0;
                var unmappedCount = 0;
                var structureTruncated = false;
                foreach (var child in root.Children)
                {
                    CollectFileSymbols(
                        requestLifetime,
                        child,
                        sourceResolution.PhysicalPath,
                        -1,
                        -1,
                        mapped,
                        ref unreadableCount,
                        ref unmappedCount,
                        ref structureTruncated);
                }

                var pageItems = mapped.Skip(request.Offset).Take(request.MaxResults).ToArray();
                var hasMore = request.Offset + pageItems.Length < mapped.Count;
                var skippedCount = unreadableCount + unmappedCount;
                var diagnostics = new List<CppSemanticDiagnostic>();
                if (unreadableCount > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "unreadable_file_symbols",
                        $"{unreadableCount} file declaration(s) could not be read."));
                }

                if (unmappedCount > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "unmapped_file_symbols",
                        $"{unmappedCount} file declaration(s) have no exact source location."));
                }

                if (structureTruncated)
                {
                    diagnostics.Add(Diagnostic(
                        "code_structure_truncated",
                        "Rider truncated the file outline; some declarations are missing."));
                }

                return new ListSymbolsInFileResponse(
                    skippedCount > 0 || structureTruncated ? "partial" : "ok",
                    sourceResolution.PhysicalPath,
                    GetUeModule(sourceResolution.PhysicalPath),
                    PrimaryCppPsiContext,
                    pageItems,
                    new CppPageInfo(
                        request.Offset,
                        pageItems.Length,
                        mapped.Count,
                        skippedCount == 0 && !structureTruncated,
                        hasMore,
                        hasMore ? request.Offset + pageItems.Length : -1,
                        skippedCount),
                    diagnostics.ToArray());
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception exception)
            {
                throw new InvalidOperationException(
                    $"Rider C++ file symbol outline failed during {stage}.",
                    exception);
            }
        }

        private void CollectFileSymbols(
            Lifetime requestLifetime,
            CodeStructureElement element,
            string physicalPath,
            int parentOutlineIndex,
            int parentDepth,
            List<CppFileSymbolOccurrence> mapped,
            ref int unreadableCount,
            ref int unmappedCount,
            ref bool structureTruncated)
        {
            requestLifetime.ThrowIfNotAlive();
            if (string.Equals(
                    element.GetType().FullName,
                    TooManyCodeStructureElementsType,
                    StringComparison.Ordinal))
            {
                structureTruncated = true;
                return;
            }

            var childParentIndex = parentOutlineIndex;
            var childParentDepth = parentDepth;
            // Rider's qualifier groups implement the declaration interface but have no declaration.
            // Traverse their children with the existing parent; the group itself is not a lost symbol.
            var declarationElement = string.Equals(element.GetType().FullName, CodeStructureQualifierElementType, StringComparison.Ordinal)
                ? null
                : element as ICodeStructureDeclarationElement;
            if (declarationElement != null)
            {
                var cppElement = declarationElement.DeclaredElement as ICppDeclaredElement;
                if (cppElement == null || !cppElement.IsValid())
                {
                    unreadableCount++;
                }
                else
                {
                    try
                    {
                        CppFileSymbolOccurrence occurrence;
                        var outlineIndex = mapped.Count;
                        var depth = parentDepth + 1;
                        if (TryBuildFileSymbolOccurrence(
                                declarationElement,
                                cppElement,
                                physicalPath,
                                outlineIndex,
                                parentOutlineIndex,
                                depth,
                                out occurrence))
                        {
                            mapped.Add(occurrence);
                            childParentIndex = outlineIndex;
                            childParentDepth = depth;
                        }
                        else
                        {
                            unmappedCount++;
                        }
                    }
                    catch (OperationCanceledException)
                    {
                        throw;
                    }
                    catch
                    {
                        unreadableCount++;
                    }
                }
            }

            foreach (var child in element.Children)
            {
                CollectFileSymbols(
                    requestLifetime,
                    child,
                    physicalPath,
                    childParentIndex,
                    childParentDepth,
                    mapped,
                    ref unreadableCount,
                    ref unmappedCount,
                    ref structureTruncated);
            }
        }

        private bool TryBuildFileSymbolOccurrence(
            ICodeStructureDeclarationElement declarationElement,
            ICppDeclaredElement cppElement,
            string physicalPath,
            int outlineIndex,
            int parentOutlineIndex,
            int depth,
            out CppFileSymbolOccurrence occurrence)
        {
            occurrence = null;
            var parserSymbol = CppCodeStructureProvider.GetSymbolFromDeclaration(declarationElement.Declaration);
            if (parserSymbol == null)
            {
                var localSymbols = cppElement.GetSymbols()
                    .OfType<ICppParserSymbol>()
                    .Where(symbol =>
                        symbol.ContainingFile.IsValid() &&
                        string.Equals(
                            symbol.ContainingFile.Location.ToString(),
                            physicalPath,
                            StringComparison.OrdinalIgnoreCase))
                    .ToArray();
                if (localSymbols.Length != 1)
                {
                    return false;
                }

                parserSymbol = localSymbols[0];
            }

            CppSourcePosition location;
            if (!TryMapSymbol(parserSymbol, out location) ||
                !string.Equals(location.FilePath, physicalPath, StringComparison.OrdinalIgnoreCase))
            {
                return false;
            }

            var canonicalElement = CanonicalizeDeclaredElement(cppElement);
            var containingType = GetContainingType(canonicalElement);
            var kind = NormalizeKind(canonicalElement, containingType);
            occurrence = new CppFileSymbolOccurrence(
                outlineIndex,
                parentOutlineIndex,
                depth,
                canonicalElement.ShortName ?? string.Empty,
                GetQualifiedName(canonicalElement),
                kind,
                string.Equals(kind, "namespace", StringComparison.Ordinal)
                    ? string.Empty
                    : GetSignature(canonicalElement),
                containingType,
                CppSymbolUtil.IsDefinition(parserSymbol) ? "definition" : "declaration",
                location);
            return true;
        }

        public FindReferencesExecution BeginFindReferences(
            Lifetime requestLifetime,
            CppPagedTargetRequest request)
        {
            var stage = "validate request";
            try
            {
                requestLifetime.ThrowIfNotAlive();
                if (request.Offset < 0 || request.MaxResults < 1 || request.MaxResults > 200)
                {
                    return new FindReferencesExecution(
                        ReferencesFailure(
                            "not_found",
                            "invalid_paging",
                            "offset must be zero or greater and max_results must be between 1 and 200",
                            request.Offset));
                }

                stage = "resolve target";
                var resolution = ResolveTarget(request.FilePath, request.Line, request.Column);
                if (resolution.Status != "ok")
                {
                    var targets = resolution.Elements.Select(BuildSymbolSummary).ToArray();
                    return new FindReferencesExecution(
                        new FindReferencesResponse(
                            resolution.Status,
                            targets,
                            Array.Empty<CppReferenceResult>(),
                            EmptyPage(request.Offset),
                            SearchScope,
                            resolution.Diagnostics.ToArray()));
                }

                requestLifetime.ThrowIfNotAlive();
                stage = "create target pointers";
                var targetPointers = resolution.Elements
                    .Cast<IDeclaredElement>()
                    .Select(element => element.CreateElementPointer())
                    .ToArray();
                var operationId = Interlocked.Increment(ref _nextReferencesOperationId);

                stage = "schedule asynchronous find-usages operation";
                var searchDomain = _psiServices.SearchDomainFactory.CreateSearchDomain(_solution, includeLibraries: true);
                var findResults = new List<FindResult>();
                var progressIndicator = NullProgressIndicator.CreateCancellable(requestLifetime);
                var resultConsumer = new FindResultConsumer<FindResult>(
                    result => result,
                    result =>
                    {
                        findResults.Add(result);
                        return FindExecution.Continue;
                    });
                var completion = new TaskCompletionSource<bool>(
                    TaskCreationOptions.RunContinuationsAsynchronously);
                requestLifetime.OnTermination(() =>
                {
                    if (!completion.Task.IsCompleted)
                    {
                        _logger.Info(
                            "ReSharper MCP references #{0}: request lifetime terminated before Finder completion.",
                            operationId);
                    }

                    completion.TrySetCanceled();
                });
                _finderOperationManager.Run(
                    resolution.Elements.Cast<IDeclaredElement>().ToArray(),
                    elements => new CachingFinder(_psiServices, searchDomain).CreateFindUsagesOperation(
                        elements,
                        progressIndicator,
                        resultConsumer),
                    new FinderCallback(
                        () =>
                        {
                            _logger.Info(
                                "ReSharper MCP references #{0}: Finder callback completed with {1} raw result(s).",
                                operationId,
                                findResults.Count);
                            completion.TrySetResult(true);
                        },
                        message =>
                        {
                            _logger.Info(
                                "ReSharper MCP references #{0}: Finder callback reported an error: {1}",
                                operationId,
                                message);
                            completion.TrySetException(
                                new InvalidOperationException(
                                    "Rider C++ semantic reference search failed during asynchronous find-usages execution: " +
                                    message));
                        }));

                _logger.Info(
                    "ReSharper MCP references #{0}: asynchronous Finder request queued.",
                    operationId);

                return new FindReferencesExecution(
                    operationId,
                    request,
                    targetPointers,
                    resolution.Diagnostics.ToArray(),
                    findResults,
                    completion.Task);
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception exception)
            {
                throw new InvalidOperationException(
                    $"Rider C++ semantic reference search failed during {stage}.",
                    exception);
            }
        }

        public FindReferencesResponse CompleteFindReferences(
            Lifetime requestLifetime,
            FindReferencesExecution execution)
        {
            var stage = "validate completed find-usages operation";
            try
            {
                requestLifetime.ThrowIfNotAlive();
                if (execution.ImmediateResponse != null)
                {
                    return execution.ImmediateResponse;
                }

                var request = execution.Request;
                var findResults = execution.Results;
                stage = "restore target symbols";
                var restoredTargets = execution.TargetPointers
                    .Select(pointer => pointer.FindDeclaredElement())
                    .OfType<ICppDeclaredElement>()
                    .ToArray();
                var targets = restoredTargets.Select(BuildSymbolSummary).ToArray();
                var lostTargetCount = execution.TargetPointers.Length - restoredTargets.Length;

                stage = "map reference results";
                var unmappedCount = 0;
                var unrealAssetResultCount = 0;
                var otherUnsupportedResultCount = 0;
                var seenLocations = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                var mappedResults = new List<CppReferenceResult>(findResults.Count);
                foreach (var findResult in findResults)
                {
                    requestLifetime.ThrowIfNotAlive();
                    IDocument resultDocument = null;
                    VirtualFileSystemPath resultPath = null;
                    DocumentCoords? mappedCoords = null;
                    if (findResult is IFindResultReference referenceResult)
                    {
                        var reference = referenceResult.Reference;
                        var node = reference?.GetTreeNode();
                        if (node == null || !reference.IsValid() || !node.IsValid())
                        {
                            unmappedCount++;
                            continue;
                        }

                        var range = reference.GetDocumentRange();
                        if (range.IsValid() && range.IsNormalized && range.Document != null)
                        {
                            var directPath = _documentManager.TryGetDocumentFilePath(range.Document);
                            if (directPath != null && !directPath.IsEmpty)
                            {
                                resultDocument = range.Document;
                                resultPath = directPath;
                                mappedCoords = range.StartOffset.ToDocumentCoords();
                            }
                        }

                        if (!mappedCoords.HasValue)
                        {
                            var sourceFile = node.GetSourceFile();
                            var sourceDocument = sourceFile?.Document;
                            var sourcePath = sourceFile?.GetLocation();
                            var treeRange = reference.GetTreeTextRange();
                            var treeStartOffset = treeRange.StartOffset.Offset;
                            if (sourceFile == null ||
                                !sourceFile.IsValid() ||
                                sourceDocument == null ||
                                sourcePath == null ||
                                sourcePath.IsEmpty ||
                                !treeRange.IsValid() ||
                                !treeRange.IsNormalized ||
                                treeStartOffset < 0 ||
                                treeStartOffset > sourceDocument.GetTextLength())
                            {
                                unmappedCount++;
                                continue;
                            }

                            resultDocument = sourceDocument;
                            resultPath = sourcePath;
                            mappedCoords = new DocumentOffset(sourceDocument, treeStartOffset).ToDocumentCoords();
                        }
                    }
                    else if (findResult is FindResultText textResult)
                    {
                        var range = textResult.DocumentRange;
                        var sourceFile = textResult.SourceFile;
                        var sourcePath = sourceFile?.GetLocation();
                        if (!range.IsValid() ||
                            !range.IsNormalized ||
                            sourceFile == null ||
                            !sourceFile.IsValid() ||
                            sourcePath == null ||
                            sourcePath.IsEmpty)
                        {
                            unmappedCount++;
                            continue;
                        }

                        var rangeDocument = range.Document;
                        if (rangeDocument == null)
                        {
                            unmappedCount++;
                            continue;
                        }

                        resultDocument = rangeDocument;
                        resultPath = _documentManager.TryGetDocumentFilePath(rangeDocument);
                        if (resultPath == null || resultPath.IsEmpty)
                        {
                            resultPath = sourcePath;
                        }

                        mappedCoords = range.StartOffset.ToDocumentCoords();
                    }
                    else
                    {
                        if (findResult is UnrealAssetFindResult)
                            unrealAssetResultCount++;
                        else
                            otherUnsupportedResultCount++;
                        continue;
                    }

                    var coords = mappedCoords.Value;
                    var location = new CppSourcePosition(
                        resultPath.ToString(),
                        (int)coords.Line + 1,
                        (int)coords.Column + 1);
                    var locationKey = LocationKey(location);
                    if (!seenLocations.Add(locationKey))
                    {
                        continue;
                    }

                    var context = resultDocument.GetLineText(coords.Line).Trim();
                    if (context.Length > 500)
                    {
                        context = context.Substring(0, 500) + "…";
                    }

                    mappedResults.Add(new CppReferenceResult(location, context, "unknown"));
                }

                var ordered = mappedResults
                    .OrderBy(result => result.Location.FilePath, StringComparer.OrdinalIgnoreCase)
                    .ThenBy(result => result.Location.Line)
                    .ThenBy(result => result.Location.Column)
                    .ToArray();
                var pageItems = ordered.Skip(request.Offset).Take(request.MaxResults).ToArray();
                var hasMore = request.Offset + pageItems.Length < ordered.Length;
                var diagnostics = new List<CppSemanticDiagnostic>(execution.Diagnostics);
                var unsupportedResultCount = unrealAssetResultCount + otherUnsupportedResultCount;
                var skippedCount = unmappedCount + unsupportedResultCount;
                if (lostTargetCount > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "target_lost_after_search",
                        "The target became unavailable during the search; retry the query."));
                }

                if (unmappedCount > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "unmapped_references",
                        $"{unmappedCount} reference result(s) could not be mapped to source locations."));
                }

                if (unsupportedResultCount > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "unsupported_reference_results",
                        UnsupportedReferencesMessage(unrealAssetResultCount, otherUnsupportedResultCount)));
                }

                _logger.Info(
                    "ReSharper MCP references #{0}: mapped {1} result(s), unmapped {2}, unsupported {3}, target pointers lost {4}.",
                    execution.OperationId,
                    ordered.Length,
                    unmappedCount,
                    unsupportedResultCount,
                    lostTargetCount);

                return new FindReferencesResponse(
                    skippedCount > 0 || lostTargetCount > 0 ? "partial" : "ok",
                    targets,
                    pageItems,
                    new CppPageInfo(
                        request.Offset,
                        pageItems.Length,
                        ordered.Length,
                        skippedCount == 0 && lostTargetCount == 0,
                        hasMore,
                        hasMore ? request.Offset + pageItems.Length : -1,
                        skippedCount),
                    SearchScope,
                    diagnostics.ToArray());
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception exception)
            {
                throw new InvalidOperationException(
                    $"Rider C++ semantic reference search failed during {stage}.",
                    exception);
            }
        }

        private static string UnsupportedReferencesMessage(int assetCount, int otherCount)
        {
            if (otherCount == 0)
                return $"{assetCount} Unreal asset reference(s) are not included in these source results.";
            if (assetCount == 0)
                return $"{otherCount} unsupported reference result(s) are not included.";
            return $"{assetCount} Unreal asset reference(s) and {otherCount} unsupported reference result(s) are not included.";
        }

        private sealed class FinderCallback : IFinderAsyncCallback
        {
            private readonly Action _complete;
            private readonly Action<string> _error;

            public FinderCallback(Action complete, Action<string> error)
            {
                _complete = complete;
                _error = error;
            }

            public void Complete()
            {
                _complete();
            }

            public void Error(string message)
            {
                _error(message);
            }
        }

        public BaseTypesResponse GetDirectBaseTypes(Lifetime requestLifetime, CppPagedTargetRequest request)
        {
            var stage = "validate request";
            try
            {
                requestLifetime.ThrowIfNotAlive();
                CppSemanticDiagnostic pagingDiagnostic;
                if (!TryValidatePaging(request.Offset, request.MaxResults, out pagingDiagnostic))
                {
                    return BaseTypesFailure(
                        "not_found",
                        pagingDiagnostic.Code,
                        pagingDiagnostic.Message,
                        request.Offset);
                }

                stage = "resolve target";
                var resolution = ResolveTarget(request.FilePath, request.Line, request.Column);
                requestLifetime.ThrowIfNotAlive();
                var targets = resolution.Elements.Select(BuildSymbolSummary).ToArray();
                requestLifetime.ThrowIfNotAlive();
                if (resolution.Status != "ok")
                {
                    return new BaseTypesResponse(
                        resolution.Status,
                        targets,
                        Array.Empty<CppBaseTypeResult>(),
                        EmptyPage(request.Offset),
                        SearchScope,
                        resolution.Diagnostics.ToArray());
                }

                var target = resolution.Elements[0];
                if (!CppDeclaredElementType.IsType(target.GetElementType()))
                {
                    return new BaseTypesResponse(
                        "unsupported",
                        targets,
                        Array.Empty<CppBaseTypeResult>(),
                        EmptyPage(request.Offset),
                        SearchScope,
                        new[]
                        {
                            Diagnostic(
                                "target_is_not_type",
                                "get_direct_base_types requires a C++ type target.")
                        });
                }

                stage = "find direct base types";
                var progress = NullProgressIndicator.CreateCancellable(requestLifetime);
                int unavailableOccurrences;
                var baseElements = GetCppOccurrenceElements(
                    CppContextSearchUtil.FindBases(target, progress),
                    out unavailableOccurrences);
                requestLifetime.ThrowIfNotAlive();

                stage = "map direct base types";
                var skippedCount = 0;
                var mapped = new List<CppBaseTypeResult>();
                foreach (var baseElement in baseElements)
                {
                    requestLifetime.ThrowIfNotAlive();
                    var summary = BuildSymbolSummary(baseElement);
                    if (!summary.HasNavigation)
                    {
                        skippedCount++;
                        continue;
                    }

                    mapped.Add(new CppBaseTypeResult(
                        summary,
                        "unknown",
                        "unknown",
                        "unknown",
                        "unknown"));
                }

                var ordered = mapped
                    .GroupBy(result => SymbolIdentityKey(result.Symbol), StringComparer.OrdinalIgnoreCase)
                    .Select(group => group.First())
                    .OrderBy(result => NavigationPath(result.Symbol), StringComparer.OrdinalIgnoreCase)
                    .ThenBy(result => NavigationLine(result.Symbol))
                    .ThenBy(result => NavigationColumn(result.Symbol))
                    .ThenBy(result => result.Symbol.QualifiedName, StringComparer.Ordinal)
                    .ThenBy(result => result.Symbol.Signature, StringComparer.Ordinal)
                    .ToArray();
                var pageItems = ordered.Skip(request.Offset).Take(request.MaxResults).ToArray();
                var hasMore = request.Offset + pageItems.Length < ordered.Length;
                var diagnostics = new List<CppSemanticDiagnostic>(resolution.Diagnostics);
                if (unavailableOccurrences > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "unavailable_hierarchy_results",
                        $"{unavailableOccurrences} related result(s) could not be read."));
                }

                if (skippedCount > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "unmapped_base_types",
                        $"{skippedCount} direct base type(s) have no navigable source location."));
                }

                var totalSkippedCount = skippedCount + unavailableOccurrences;
                return new BaseTypesResponse(
                    totalSkippedCount > 0 ? "partial" : "ok",
                    targets,
                    pageItems,
                    new CppPageInfo(
                        request.Offset,
                        pageItems.Length,
                        ordered.Length,
                        totalSkippedCount == 0,
                        hasMore,
                        hasMore ? request.Offset + pageItems.Length : -1,
                        totalSkippedCount),
                    SearchScope,
                    diagnostics.ToArray());
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception exception)
            {
                throw new InvalidOperationException(
                    $"Rider C++ direct base type query failed during {stage}.",
                    exception);
            }
        }

        public RelatedSymbolsResponse FindDerivedTypes(Lifetime requestLifetime, CppPagedTargetRequest request)
        {
            var stage = "validate request";
            try
            {
                requestLifetime.ThrowIfNotAlive();
                CppSemanticDiagnostic pagingDiagnostic;
                if (!TryValidatePaging(request.Offset, request.MaxResults, out pagingDiagnostic))
                {
                    return RelatedSymbolsFailure(
                        "not_found",
                        pagingDiagnostic.Code,
                        pagingDiagnostic.Message,
                        request.Offset);
                }

                stage = "resolve target";
                var resolution = ResolveTarget(request.FilePath, request.Line, request.Column);
                requestLifetime.ThrowIfNotAlive();
                var targets = resolution.Elements.Select(BuildSymbolSummary).ToArray();
                requestLifetime.ThrowIfNotAlive();
                if (resolution.Status != "ok")
                {
                    return new RelatedSymbolsResponse(
                        resolution.Status,
                        targets,
                        Array.Empty<CppRelatedSymbolResult>(),
                        EmptyPage(request.Offset),
                        SearchScope,
                        resolution.Diagnostics.ToArray());
                }

                var target = resolution.Elements[0];
                if (!CppDeclaredElementType.IsType(target.GetElementType()))
                {
                    return new RelatedSymbolsResponse(
                        "unsupported",
                        targets,
                        Array.Empty<CppRelatedSymbolResult>(),
                        EmptyPage(request.Offset),
                        SearchScope,
                        new[]
                        {
                            Diagnostic(
                                "target_is_not_type",
                                "find_derived_types requires a C++ type target.")
                        });
                }

                stage = "find direct derived types";
                int unavailableDirectOccurrences;
                var directElements = GetCppOccurrenceElements(
                    CppContextSearchUtil.FindInheritors(
                        target,
                        recursive: false,
                        onlyImplementations: false,
                        NullProgressIndicator.CreateCancellable(requestLifetime)),
                    out unavailableDirectOccurrences);
                requestLifetime.ThrowIfNotAlive();
                var directKeys = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                foreach (var directElement in directElements)
                {
                    requestLifetime.ThrowIfNotAlive();
                    directKeys.Add(SemanticIdentityKey(BuildSymbolSummary(directElement)));
                }

                stage = "find all derived types";
                int unavailableRecursiveOccurrences;
                var recursiveElements = GetCppOccurrenceElements(
                    CppContextSearchUtil.FindInheritors(
                        target,
                        recursive: true,
                        onlyImplementations: false,
                        NullProgressIndicator.CreateCancellable(requestLifetime)),
                    out unavailableRecursiveOccurrences);
                requestLifetime.ThrowIfNotAlive();

                stage = "map derived types";
                var skippedCount = 0;
                var mapped = new List<CppRelatedSymbolResult>();
                foreach (var derivedElement in recursiveElements)
                {
                    requestLifetime.ThrowIfNotAlive();
                    var summary = BuildSymbolSummary(derivedElement);
                    if (!summary.HasNavigation)
                    {
                        skippedCount++;
                        continue;
                    }

                    mapped.Add(new CppRelatedSymbolResult(
                        summary,
                        RelatedSymbolRelation(directKeys.Contains(SemanticIdentityKey(summary)), unavailableDirectOccurrences == 0)));
                }

                var ordered = mapped
                    .GroupBy(result => SemanticIdentityKey(result.Symbol), StringComparer.OrdinalIgnoreCase)
                    .Select(group => group.OrderBy(result => result.Relation == "direct" ? 0 : 1).First())
                    .OrderBy(result => NavigationPath(result.Symbol), StringComparer.OrdinalIgnoreCase)
                    .ThenBy(result => NavigationLine(result.Symbol))
                    .ThenBy(result => NavigationColumn(result.Symbol))
                    .ThenBy(result => result.Symbol.QualifiedName, StringComparer.Ordinal)
                    .ThenBy(result => result.Symbol.Signature, StringComparer.Ordinal)
                    .ToArray();
                var pageItems = ordered.Skip(request.Offset).Take(request.MaxResults).ToArray();
                var hasMore = request.Offset + pageItems.Length < ordered.Length;
                var diagnostics = new List<CppSemanticDiagnostic>(resolution.Diagnostics);
                if (unavailableDirectOccurrences > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "incomplete_direct_relations",
                        "Some direct relationships could not be verified; unconfirmed relations are marked unknown."));
                }

                if (unavailableRecursiveOccurrences > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "unavailable_hierarchy_results",
                        $"{unavailableRecursiveOccurrences} related result(s) could not be read."));
                }

                if (skippedCount > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "unmapped_derived_types",
                        $"{skippedCount} derived type(s) have no navigable source location."));
                }

                var totalSkippedCount = skippedCount + unavailableRecursiveOccurrences;
                return new RelatedSymbolsResponse(
                    totalSkippedCount > 0 || unavailableDirectOccurrences > 0 ? "partial" : "ok",
                    targets,
                    pageItems,
                    new CppPageInfo(
                        request.Offset,
                        pageItems.Length,
                        ordered.Length,
                        totalSkippedCount == 0,
                        hasMore,
                        hasMore ? request.Offset + pageItems.Length : -1,
                        totalSkippedCount),
                    SearchScope,
                    diagnostics.ToArray());
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception exception)
            {
                throw new InvalidOperationException(
                    $"Rider C++ derived type query failed during {stage}.",
                    exception);
            }
        }

        public RelatedSymbolsResponse GetDirectOverriddenMembers(
            Lifetime requestLifetime,
            CppPagedTargetRequest request)
        {
            var stage = "validate request";
            try
            {
                requestLifetime.ThrowIfNotAlive();
                CppSemanticDiagnostic pagingDiagnostic;
                if (!TryValidatePaging(request.Offset, request.MaxResults, out pagingDiagnostic))
                {
                    return RelatedSymbolsFailure(
                        "not_found",
                        pagingDiagnostic.Code,
                        pagingDiagnostic.Message,
                        request.Offset);
                }

                stage = "resolve target";
                var resolution = ResolveTarget(request.FilePath, request.Line, request.Column);
                requestLifetime.ThrowIfNotAlive();
                var targets = resolution.Elements.Select(BuildSymbolSummary).ToArray();
                requestLifetime.ThrowIfNotAlive();
                if (resolution.Status != "ok")
                {
                    return new RelatedSymbolsResponse(
                        resolution.Status,
                        targets,
                        Array.Empty<CppRelatedSymbolResult>(),
                        EmptyPage(request.Offset),
                        SearchScope,
                        resolution.Diagnostics.ToArray());
                }

                var target = resolution.Elements[0];
                if (!CppDeclaredElementType.IsFunction(target.GetElementType()))
                {
                    return new RelatedSymbolsResponse(
                        "unsupported",
                        targets,
                        Array.Empty<CppRelatedSymbolResult>(),
                        EmptyPage(request.Offset),
                        SearchScope,
                        new[]
                        {
                            Diagnostic(
                                "target_is_not_function",
                                "get_direct_overridden_members requires a C++ function or method target.")
                        });
                }

                stage = "find direct overridden members";
                int unavailableOccurrences;
                var baseElements = GetCppOccurrenceElements(
                    CppContextSearchUtil.FindBases(
                        target,
                        NullProgressIndicator.CreateCancellable(requestLifetime)),
                    out unavailableOccurrences);
                requestLifetime.ThrowIfNotAlive();

                stage = "map direct overridden members";
                var skippedCount = 0;
                var mapped = new List<CppRelatedSymbolResult>();
                foreach (var baseElement in baseElements)
                {
                    requestLifetime.ThrowIfNotAlive();
                    var summary = BuildSymbolSummary(baseElement);
                    if (!summary.HasNavigation)
                    {
                        skippedCount++;
                        continue;
                    }

                    mapped.Add(new CppRelatedSymbolResult(summary, "direct"));
                }

                var ordered = mapped
                    .GroupBy(result => SemanticIdentityKey(result.Symbol), StringComparer.OrdinalIgnoreCase)
                    .Select(group => group.First())
                    .OrderBy(result => NavigationPath(result.Symbol), StringComparer.OrdinalIgnoreCase)
                    .ThenBy(result => NavigationLine(result.Symbol))
                    .ThenBy(result => NavigationColumn(result.Symbol))
                    .ThenBy(result => result.Symbol.QualifiedName, StringComparer.Ordinal)
                    .ThenBy(result => result.Symbol.Signature, StringComparer.Ordinal)
                    .ToArray();
                var pageItems = ordered.Skip(request.Offset).Take(request.MaxResults).ToArray();
                var hasMore = request.Offset + pageItems.Length < ordered.Length;
                var diagnostics = new List<CppSemanticDiagnostic>(resolution.Diagnostics);
                if (unavailableOccurrences > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "unavailable_hierarchy_results",
                        $"{unavailableOccurrences} related result(s) could not be read."));
                }

                if (skippedCount > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "unmapped_overridden_members",
                        $"{skippedCount} overridden member(s) have no navigable source location."));
                }

                var totalSkippedCount = skippedCount + unavailableOccurrences;
                return new RelatedSymbolsResponse(
                    totalSkippedCount > 0 ? "partial" : "ok",
                    targets,
                    pageItems,
                    new CppPageInfo(
                        request.Offset,
                        pageItems.Length,
                        ordered.Length,
                        totalSkippedCount == 0,
                        hasMore,
                        hasMore ? request.Offset + pageItems.Length : -1,
                        totalSkippedCount),
                    SearchScope,
                    diagnostics.ToArray());
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception exception)
            {
                throw new InvalidOperationException(
                    $"Rider C++ direct overridden member query failed during {stage}.",
                    exception);
            }
        }

        public RelatedSymbolsResponse FindOverridingMembers(
            Lifetime requestLifetime,
            CppPagedTargetRequest request)
        {
            var stage = "validate request";
            try
            {
                requestLifetime.ThrowIfNotAlive();
                CppSemanticDiagnostic pagingDiagnostic;
                if (!TryValidatePaging(request.Offset, request.MaxResults, out pagingDiagnostic))
                {
                    return RelatedSymbolsFailure(
                        "not_found",
                        pagingDiagnostic.Code,
                        pagingDiagnostic.Message,
                        request.Offset);
                }

                stage = "resolve target";
                var resolution = ResolveTarget(request.FilePath, request.Line, request.Column);
                requestLifetime.ThrowIfNotAlive();
                var targets = resolution.Elements.Select(BuildSymbolSummary).ToArray();
                requestLifetime.ThrowIfNotAlive();
                if (resolution.Status != "ok")
                {
                    return new RelatedSymbolsResponse(
                        resolution.Status,
                        targets,
                        Array.Empty<CppRelatedSymbolResult>(),
                        EmptyPage(request.Offset),
                        SearchScope,
                        resolution.Diagnostics.ToArray());
                }

                var target = resolution.Elements[0];
                if (!CppDeclaredElementType.IsFunction(target.GetElementType()))
                {
                    return new RelatedSymbolsResponse(
                        "unsupported",
                        targets,
                        Array.Empty<CppRelatedSymbolResult>(),
                        EmptyPage(request.Offset),
                        SearchScope,
                        new[]
                        {
                            Diagnostic(
                                "target_is_not_function",
                                "find_overriding_members requires a C++ function or method target.")
                        });
                }

                if (!CppContextSearchUtil.CanHaveImplementations(target))
                {
                    return new RelatedSymbolsResponse(
                        "unsupported",
                        targets,
                        Array.Empty<CppRelatedSymbolResult>(),
                        EmptyPage(request.Offset),
                        SearchScope,
                        new[]
                        {
                            Diagnostic(
                                "target_cannot_have_implementations",
                                "The selected member does not support an overriding-member query.")
                        });
                }

                stage = "find direct overriding members";
                int unavailableDirectOccurrences;
                var directElements = GetCppOccurrenceElements(
                    CppContextSearchUtil.FindInheritors(
                        target,
                        recursive: false,
                        onlyImplementations: true,
                        NullProgressIndicator.CreateCancellable(requestLifetime)),
                    out unavailableDirectOccurrences);
                requestLifetime.ThrowIfNotAlive();
                var directKeys = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                foreach (var directElement in directElements)
                {
                    requestLifetime.ThrowIfNotAlive();
                    directKeys.Add(SemanticIdentityKey(BuildSymbolSummary(directElement)));
                }

                stage = "find all overriding members";
                int unavailableRecursiveOccurrences;
                var recursiveElements = GetCppOccurrenceElements(
                    CppContextSearchUtil.FindInheritors(
                        target,
                        recursive: true,
                        onlyImplementations: true,
                        NullProgressIndicator.CreateCancellable(requestLifetime)),
                    out unavailableRecursiveOccurrences);
                requestLifetime.ThrowIfNotAlive();

                stage = "map overriding members";
                var skippedCount = 0;
                var mapped = new List<CppRelatedSymbolResult>();
                foreach (var overridingElement in recursiveElements)
                {
                    requestLifetime.ThrowIfNotAlive();
                    var summary = BuildSymbolSummary(overridingElement);
                    if (!summary.HasNavigation)
                    {
                        skippedCount++;
                        continue;
                    }

                    mapped.Add(new CppRelatedSymbolResult(
                        summary,
                        RelatedSymbolRelation(directKeys.Contains(SemanticIdentityKey(summary)), unavailableDirectOccurrences == 0)));
                }

                var ordered = mapped
                    .GroupBy(result => SemanticIdentityKey(result.Symbol), StringComparer.OrdinalIgnoreCase)
                    .Select(group => group.OrderBy(result => result.Relation == "direct" ? 0 : 1).First())
                    .OrderBy(result => NavigationPath(result.Symbol), StringComparer.OrdinalIgnoreCase)
                    .ThenBy(result => NavigationLine(result.Symbol))
                    .ThenBy(result => NavigationColumn(result.Symbol))
                    .ThenBy(result => result.Symbol.QualifiedName, StringComparer.Ordinal)
                    .ThenBy(result => result.Symbol.Signature, StringComparer.Ordinal)
                    .ToArray();
                var pageItems = ordered.Skip(request.Offset).Take(request.MaxResults).ToArray();
                var hasMore = request.Offset + pageItems.Length < ordered.Length;
                var diagnostics = new List<CppSemanticDiagnostic>(resolution.Diagnostics);
                if (unavailableDirectOccurrences > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "incomplete_direct_relations",
                        "Some direct relationships could not be verified; unconfirmed relations are marked unknown."));
                }

                if (unavailableRecursiveOccurrences > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "unavailable_hierarchy_results",
                        $"{unavailableRecursiveOccurrences} related result(s) could not be read."));
                }

                if (skippedCount > 0)
                {
                    diagnostics.Add(Diagnostic(
                        "unmapped_overriding_members",
                        $"{skippedCount} overriding member(s) have no navigable source location."));
                }

                var totalSkippedCount = skippedCount + unavailableRecursiveOccurrences;
                return new RelatedSymbolsResponse(
                    totalSkippedCount > 0 || unavailableDirectOccurrences > 0 ? "partial" : "ok",
                    targets,
                    pageItems,
                    new CppPageInfo(
                        request.Offset,
                        pageItems.Length,
                        ordered.Length,
                        totalSkippedCount == 0,
                        hasMore,
                        hasMore ? request.Offset + pageItems.Length : -1,
                        totalSkippedCount),
                    SearchScope,
                    diagnostics.ToArray());
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception exception)
            {
                throw new InvalidOperationException(
                    $"Rider C++ overriding member query failed during {stage}.",
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
                Array.Empty<CppSemanticDiagnostic>());
        }

        private TargetResolution ResolveTarget(string filePath, int line, int column)
        {
            if (line < 1 || column < 1)
            {
                return TargetResolution.Failure(
                    "not_found",
                    Diagnostic("invalid_position", "line and column must be 1-based positive integers"));
            }

            var sourceResolution = ResolveSourceFile(filePath);
            if (sourceResolution.Status != "ok")
            {
                return new TargetResolution(
                    sourceResolution.Status,
                    Array.Empty<ICppDeclaredElement>(),
                    sourceResolution.Diagnostics);
            }

            var sourceFile = sourceResolution.SourceFile;
            var document = sourceFile.Document;
            var lineIndex = (TypedDocLine)(line - 1);
            var columnIndex = (TypedDocColumn)(column - 1);
            var lineCount = (int)document.GetLineCount();
            if (line > lineCount)
            {
                return TargetResolution.Failure(
                    "not_found",
                    Diagnostic(
                        "position_out_of_range",
                        $"Line {line} is outside the document, which has {lineCount} lines."));
            }

            var lineLength = (int)document.GetLineLength(lineIndex);
            if (column > lineLength + 1)
            {
                return TargetResolution.Failure(
                    "not_found",
                    Diagnostic(
                        "position_out_of_range",
                        $"Column {column} is outside line {line}, whose maximum position is {lineLength + 1}."));
            }

            var offset = document.GetOffsetByCoordsSafe(new DocumentCoords(lineIndex, columnIndex));
            if (!offset.HasValue)
            {
                return TargetResolution.Failure(
                    "not_found",
                    Diagnostic(
                        "position_not_mapped",
                        "The source position could not be located in the current document."));
            }

            bool hasPsiFilesWithOffset;
            var declaredElements = TextControlToPsi.GetDeclaredElements(
                    _solution,
                    new DocumentOffset(document, offset.Value),
                    SourceFilesMask.INCLUDE_SHARED_FILES,
                    out hasPsiFilesWithOffset)
                .OfType<ICppDeclaredElement>()
                .Where(element => element.IsValid())
                .Distinct()
                .ToArray();

            if (!hasPsiFilesWithOffset)
            {
                return TargetResolution.Failure(
                    "not_indexed",
                    Diagnostic(
                        "psi_not_available",
                        "C++ semantic data is not available at this position yet."));
            }

            if (declaredElements.Length == 0)
            {
                return TargetResolution.Failure(
                    "not_found",
                    Diagnostic(
                        "symbol_not_found",
                        "No C++ symbol resolved here; place the position on its identifier."));
            }

            if (declaredElements.Length > 1)
            {
                return new TargetResolution(
                    "ambiguous",
                    declaredElements,
                    new[]
                    {
                        Diagnostic(
                            "ambiguous_target",
                            $"{declaredElements.Length} C++ targets match; retry at a candidate's navigation position.")
                    });
            }

            return new TargetResolution(
                "ok",
                declaredElements,
                Array.Empty<CppSemanticDiagnostic>());
        }

        private ICppDeclaredElement CanonicalizeDeclaredElement(ICppDeclaredElement element)
        {
            element = CppDeclaredElementUtil.GetInnerIfCppDeclaredElement(element) as ICppDeclaredElement ?? element;
            var existingLinkage = element as CppLinkageEntityDeclaredElement;
            if (existingLinkage != null && existingLinkage.GetLinkageEntity() != null)
            {
                return existingLinkage;
            }

            var parserSymbols = element.GetSymbols()
                .OfType<ICppParserSymbol>()
                .ToArray();
            if (parserSymbols.Length == 0)
            {
                // Generated members can carry a resolve identity without any local parser symbols.
                var resolvedIdentity = CppDeclaredElementUtil.GetLinkageEntityFromDeclaredElement(element);
                return resolvedIdentity == null
                    ? element
                    : new CppLinkageEntityDeclaredElement(_psiServices, resolvedIdentity);
            }

            var linkageEntities = _globalSymbolCache.LinkageCache
                .FindEntitiesBySymbols(parserSymbols, null)
                .Values
                .Distinct()
                .ToArray();
            // Rider retains unresolved parser symbols as null dictionary values.
            // Do not wrap null or discard unresolved candidates to manufacture uniqueness.
            if (linkageEntities.Length != 1 || linkageEntities[0] == null)
            {
                return element;
            }

            return new CppLinkageEntityDeclaredElement(_psiServices, linkageEntities[0]);
        }

        private CppInspectedSymbol BuildInspectedSymbol(ICppDeclaredElement element)
        {
            var canonicalElement = CanonicalizeDeclaredElement(element);
            var summary = BuildSymbolSummaryCore(canonicalElement);
            var locations = GetSymbolLocations(canonicalElement);
            return new CppInspectedSymbol(
                summary,
                locations.Declarations,
                locations.Definitions,
                GetAccessibility(canonicalElement),
                "unknown",
                "unknown",
                "unknown");
        }

        private CppSymbolSummary BuildSymbolSummary(ICppDeclaredElement element)
        {
            return BuildSymbolSummaryCore(CanonicalizeDeclaredElement(element));
        }

        private CppSymbolSummary BuildSymbolSummaryCore(ICppDeclaredElement element)
        {
            var locations = GetSymbolLocations(element);
            var navigation = locations.Definitions.FirstOrDefault(CanResolveNavigation)
                             ?? locations.Declarations.FirstOrDefault(CanResolveNavigation)
                             ?? locations.Definitions.FirstOrDefault()
                             ?? locations.Declarations.FirstOrDefault()
                             ?? EmptyPosition();
            var containingType = GetContainingType(element);
            return new CppSymbolSummary(
                element.ShortName ?? string.Empty,
                GetQualifiedName(element),
                NormalizeKind(element, containingType),
                GetSignature(element),
                containingType,
                navigation.FilePath.Length > 0 ? GetUeModule(navigation.FilePath) : string.Empty,
                navigation,
                navigation.FilePath.Length > 0,
                locations.Declarations.Length,
                locations.Definitions.Length);
        }

        private SymbolLocations GetSymbolLocations(ICppDeclaredElement element)
        {
            var declarations = new List<CppSourcePosition>();
            var definitions = new List<CppSourcePosition>();
            var seenDeclarations = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            var seenDefinitions = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var symbol in element.GetSymbols().Where(symbol => symbol != null))
            {
                CppSourcePosition position;
                if (!TryMapSymbol(symbol, out position))
                {
                    continue;
                }

                var isDefinition = CppSymbolUtil.IsDefinition(symbol);
                var seen = isDefinition ? seenDefinitions : seenDeclarations;
                if (!seen.Add(LocationKey(position)))
                {
                    continue;
                }

                (isDefinition ? definitions : declarations).Add(position);
            }

            return new SymbolLocations(
                OrderPositions(declarations),
                OrderPositions(definitions));
        }

        private bool TryMapSymbol(ICppSymbol symbol, out CppSourcePosition position)
        {
            position = EmptyPosition();
            var containingFile = symbol.ContainingFile;
            if (!containingFile.IsValid())
            {
                return false;
            }

            var path = containingFile.Location;
            if (path == null || path.IsEmpty)
            {
                return false;
            }

            var range = symbol.LocateDocumentRange(_solution);
            if (range.IsValid() && range.IsNormalized && range.Document != null)
            {
                var coords = range.StartOffset.ToDocumentCoords();
                position = new CppSourcePosition(
                    path.ToString(),
                    (int)coords.Line + 1,
                    (int)coords.Column + 1);
                return true;
            }

            var document = containingFile.TryGetDocument(_solution);
            var textOffset = symbol.LocateTextOffset();
            if (document == null || textOffset < 0 || textOffset > document.GetTextLength())
            {
                return false;
            }

            var fallbackCoords = new DocumentOffset(document, textOffset).ToDocumentCoords();
            position = new CppSourcePosition(
                path.ToString(),
                (int)fallbackCoords.Line + 1,
                (int)fallbackCoords.Column + 1);
            return true;
        }

        private bool CanResolveNavigation(CppSourcePosition position)
        {
            if (string.IsNullOrWhiteSpace(position.FilePath))
            {
                return false;
            }

            try
            {
                return ResolveTarget(position.FilePath, position.Line, position.Column).Status == "ok";
            }
            catch
            {
                return false;
            }
        }

        private static string GetQualifiedName(ICppDeclaredElement element)
        {
            var qualifiedNames = element.GetSymbols()
                .OfType<ICppParserSymbol>()
                .Select(symbol => symbol.Name.ToString())
                .Where(name => !string.IsNullOrWhiteSpace(name))
                .Distinct(StringComparer.Ordinal)
                .OrderByDescending(name => name.Length)
                .ThenBy(name => name, StringComparer.Ordinal)
                .ToArray();
            var containingType = GetContainingType(element);
            var containingNamespace = GetContainingNamespace(element);
            var symbolName = qualifiedNames.FirstOrDefault() ?? element.ShortName ?? string.Empty;
            if (symbolName.Contains("::") ||
                (string.IsNullOrWhiteSpace(containingType) && string.IsNullOrWhiteSpace(containingNamespace)))
            {
                return symbolName;
            }

            return string.Join(
                "::",
                new[] { containingNamespace, containingType, symbolName }
                    .Where(part => !string.IsNullOrWhiteSpace(part)));
        }

        private static string GetContainingType(ICppDeclaredElement element)
        {
            CppDeclaredElementType ignored;
            var linkage = element as CppLinkageEntityDeclaredElement;
            if (linkage != null)
            {
                return linkage.PresentContainingType(out ignored) ?? string.Empty;
            }

            var resolve = element as CppResolveEntityDeclaredElement;
            return resolve?.PresentContainingType(out ignored) ?? string.Empty;
        }

        private static string GetContainingNamespace(ICppDeclaredElement element)
        {
            var linkage = element as CppLinkageEntityDeclaredElement;
            if (linkage != null)
            {
                return linkage.PresentContainingNamespace() ?? string.Empty;
            }

            var resolve = element as CppResolveEntityDeclaredElement;
            return resolve?.PresentContainingNamespace() ?? string.Empty;
        }

        private static string GetSignature(ICppDeclaredElement element)
        {
            var elementType = element.GetElementType();
            if (CppDeclaredElementType.IsType(elementType) || elementType == CppDeclaredElementTypes.NAMESPACE)
            {
                return string.Empty;
            }

            var linkage = element as CppLinkageEntityDeclaredElement;
            if (linkage != null)
            {
                return PresentSignature(linkage.GetCppType());
            }

            var resolve = element as CppResolveEntityDeclaredElement;
            return resolve != null ? PresentSignature(resolve.GetCppType()) : string.Empty;
        }

        private static string PresentSignature(CppQualType cppType)
        {
            var presenter = CppPresenter.CreatePresenterForDebug();
            return CppTypePresenterUtil.PresentType(presenter, cppType);
        }

        private static string NormalizeKind(ICppDeclaredElement element, string containingType)
        {
            var elementType = element.GetElementType();
            if (CppDeclaredElementType.IsFunction(elementType))
            {
                return string.IsNullOrWhiteSpace(containingType) ? "function" : "method";
            }

            if (elementType == CppDeclaredElementTypes.CONSTRUCTOR) return "constructor";
            if (elementType == CppDeclaredElementTypes.DESTRUCTOR) return "destructor";
            if (elementType == CppDeclaredElementTypes.CONVERSION_OPERATOR) return "conversion_operator";
            if (elementType == CppDeclaredElementTypes.LITERAL_OPERATOR) return "literal_operator";
            if (elementType == CppDeclaredElementTypes.MEMBER_OPERATOR) return "member_operator";
            if (elementType == CppDeclaredElementTypes.GLOBAL_OPERATOR) return "global_operator";

            if (elementType == CppDeclaredElementTypes.ENUM) return "enum";
            if (elementType == CppDeclaredElementTypes.STRUCT) return "struct";
            if (elementType == CppDeclaredElementTypes.UNION) return "union";
            if (elementType == CppDeclaredElementTypes.CLASS ||
                elementType == CppDeclaredElementTypes.__INTERFACE) return "class";
            if (CppDeclaredElementType.IsType(elementType))
            {
                return "type";
            }

            if (elementType == CppDeclaredElementTypes.NAMESPACE) return "namespace";
            if (elementType == CppDeclaredElementTypes.ENUMERATOR) return "enumerator";
            if (elementType == CppDeclaredElementTypes.CLASS_FIELD ||
                elementType == CppDeclaredElementTypes.STRUCT_FIELD ||
                elementType == CppDeclaredElementTypes.UNION_MEMBER) return "field";
            if (elementType == CppDeclaredElementTypes.PARAMETER ||
                elementType == CppDeclaredElementTypes.TEMPLATE_PARAMETER ||
                elementType == CppDeclaredElementTypes.MACRO_PARAMETER) return "parameter";
            if (elementType == CppDeclaredElementTypes.GLOBAL_VARIABLE ||
                elementType == CppDeclaredElementTypes.LOCAL_VARIABLE) return "variable";
            if (elementType == CppDeclaredElementTypes.MACRO) return "macro";
            if (elementType == CppDeclaredElementTypes.TYPE_ALIAS ||
                elementType == CppDeclaredElementTypes.TYPEDEF ||
                elementType == CppDeclaredElementTypes.NAMESPACE_ALIAS) return "alias";
            if (elementType == CppDeclaredElementTypes.USING_DECLARATION) return "using_declaration";
            if (elementType == CppDeclaredElementTypes.CONCEPT) return "concept";
            if (elementType == CppDeclaredElementTypes.PROPERTY) return "property";
            if (elementType == CppDeclaredElementTypes.EVENT) return "event";
            if (elementType == CppDeclaredElementTypes.MODULE ||
                elementType == CppDeclaredElementTypes.MODULE_PARTITION) return "module";
            return "other";
        }

        private string GetUeModule(string filePath)
        {
            var path = VirtualFileSystemPath.Parse(
                filePath,
                _solution.GetInteractionContext(),
                FileSystemPathInternStrategy.DO_NOT_INTERN);
            if (path.IsEmpty)
            {
                return string.Empty;
            }

            var buildCs = _ue4ModuleNamesProvider.GetAnyBuildCsFor(path);
            return buildCs == null || buildCs.IsEmpty
                ? string.Empty
                : _ue4ModuleNamesProvider.GetUE4ModuleNameOf(buildCs) ?? string.Empty;
        }

        private static string NormalizeAccessibility(string value)
        {
            if (string.IsNullOrWhiteSpace(value)) return "unknown";
            var normalized = value.ToLowerInvariant();
            return normalized == "public" || normalized == "protected" || normalized == "private"
                ? normalized
                : "unknown";
        }

        private static string GetAccessibility(ICppDeclaredElement element)
        {
            var symbolAccessibilities = element.GetSymbols()
                .OfType<ICppParserSymbol>()
                .Select(CppResolveEntityUtil.GetAccessibility)
                .Where(accessibility => accessibility != CppAccessibility.NOT_APPLICABLE)
                .Distinct()
                .ToArray();
            return symbolAccessibilities.Length == 1
                ? NormalizeAccessibility(symbolAccessibilities[0].ToString())
                : NormalizeAccessibility(element.GetAccessibility().ToString());
        }

        private static ICppDeclaredElement[] GetCppOccurrenceElements(
            IEnumerable<IOccurrence> occurrences,
            out int unavailableOccurrences)
        {
            unavailableOccurrences = 0;
            var elements = new List<ICppDeclaredElement>();
            foreach (var occurrence in occurrences)
            {
                // Rider explicitly returns this asset shape alongside C++ inheritors.
                // It is outside the hierarchy tools' C++ source contract.
                if (occurrence is UnrealAssetOccurence)
                    continue;

                var cppOccurrence = occurrence as CppDeclaredElementOccurrence;
                var element = cppOccurrence?.OccurrenceElement?.GetValidDeclaredElement() as ICppDeclaredElement;
                if (element == null || !element.IsValid())
                {
                    // Unknown shapes and unavailable C++ elements are incomplete results, not scope exclusions.
                    unavailableOccurrences++;
                    continue;
                }

                elements.Add(element);
            }

            return elements.Distinct().ToArray();
        }

        private static string RelatedSymbolRelation(bool isDirect, bool directQueryComplete)
        {
            return isDirect ? "direct" : directQueryComplete ? "indirect" : "unknown";
        }

        private static bool TryValidatePaging(
            int offset,
            int maxResults,
            out CppSemanticDiagnostic diagnostic)
        {
            if (offset < 0 || maxResults < 1 || maxResults > 200)
            {
                diagnostic = Diagnostic(
                    "invalid_paging",
                    "offset must be zero or greater and max_results must be between 1 and 200");
                return false;
            }

            diagnostic = null;
            return true;
        }

        private static CppSourcePosition[] OrderPositions(IEnumerable<CppSourcePosition> positions)
        {
            return positions
                .OrderBy(position => position.FilePath, StringComparer.OrdinalIgnoreCase)
                .ThenBy(position => position.Line)
                .ThenBy(position => position.Column)
                .ToArray();
        }

        private static string SymbolIdentityKey(CppSymbolSummary summary)
        {
            return summary.QualifiedName + "\0" + summary.Kind + "\0" + summary.Signature + "\0" +
                   NavigationPath(summary) + "\0" + NavigationLine(summary) + "\0" + NavigationColumn(summary);
        }

        private static string SemanticIdentityKey(CppSymbolSummary summary)
        {
            return summary.QualifiedName + "\0" + summary.Kind + "\0" + summary.Signature;
        }

        private static string NavigationPath(CppSymbolSummary summary)
        {
            return summary.HasNavigation ? summary.Navigation.FilePath : string.Empty;
        }

        private static int NavigationLine(CppSymbolSummary summary)
        {
            return summary.HasNavigation ? summary.Navigation.Line : 0;
        }

        private static int NavigationColumn(CppSymbolSummary summary)
        {
            return summary.HasNavigation ? summary.Navigation.Column : 0;
        }

        private static string LocationKey(CppSourcePosition position)
        {
            return position.FilePath + "\0" + position.Line + "\0" + position.Column;
        }

        private static CppSourcePosition EmptyPosition()
        {
            return new CppSourcePosition(string.Empty, 0, 0);
        }

        private static CppPageInfo EmptyPage(int offset)
        {
            return new CppPageInfo(offset, 0, 0, true, false, -1, 0);
        }

        private static CppSemanticDiagnostic Diagnostic(string code, string message)
        {
            return new CppSemanticDiagnostic(code, message);
        }

        private static SearchSymbolsResponse SearchFailure(
            string status,
            string code,
            string message,
            int offset)
        {
            return new SearchSymbolsResponse(
                status,
                Array.Empty<CppSymbolSummary>(),
                EmptyPage(Math.Max(0, offset)),
                SearchScope,
                new[] { Diagnostic(code, message) });
        }

        private static ListSymbolsInFileResponse FileOutlineFailure(
            string status,
            string filePath,
            string code,
            string message,
            int offset)
        {
            return new ListSymbolsInFileResponse(
                status,
                filePath ?? string.Empty,
                string.Empty,
                PrimaryCppPsiContext,
                Array.Empty<CppFileSymbolOccurrence>(),
                EmptyPage(Math.Max(0, offset)),
                new[] { Diagnostic(code, message) });
        }

        private static FindReferencesResponse ReferencesFailure(
            string status,
            string code,
            string message,
            int offset)
        {
            return new FindReferencesResponse(
                status,
                Array.Empty<CppSymbolSummary>(),
                Array.Empty<CppReferenceResult>(),
                EmptyPage(Math.Max(0, offset)),
                SearchScope,
                new[] { Diagnostic(code, message) });
        }

        private static BaseTypesResponse BaseTypesFailure(
            string status,
            string code,
            string message,
            int offset)
        {
            return new BaseTypesResponse(
                status,
                Array.Empty<CppSymbolSummary>(),
                Array.Empty<CppBaseTypeResult>(),
                EmptyPage(Math.Max(0, offset)),
                SearchScope,
                new[] { Diagnostic(code, message) });
        }

        private static RelatedSymbolsResponse RelatedSymbolsFailure(
            string status,
            string code,
            string message,
            int offset)
        {
            return new RelatedSymbolsResponse(
                status,
                Array.Empty<CppSymbolSummary>(),
                Array.Empty<CppRelatedSymbolResult>(),
                EmptyPage(Math.Max(0, offset)),
                SearchScope,
                new[] { Diagnostic(code, message) });
        }

        private sealed class SourceFileResolution
        {
            public SourceFileResolution(
                string status,
                string physicalPath,
                IPsiSourceFile sourceFile,
                CppSemanticDiagnostic[] diagnostics)
            {
                Status = status;
                PhysicalPath = physicalPath;
                SourceFile = sourceFile;
                Diagnostics = diagnostics;
            }

            public string Status { get; }
            public string PhysicalPath { get; }
            public IPsiSourceFile SourceFile { get; }
            public CppSemanticDiagnostic[] Diagnostics { get; }

            public static SourceFileResolution Failure(
                string status,
                string physicalPath,
                CppSemanticDiagnostic diagnostic)
            {
                return new SourceFileResolution(
                    status,
                    physicalPath ?? string.Empty,
                    null,
                    new[] { diagnostic });
            }
        }

        private sealed class TargetResolution
        {
            public TargetResolution(
                string status,
                ICppDeclaredElement[] elements,
                CppSemanticDiagnostic[] diagnostics)
            {
                Status = status;
                Elements = elements;
                Diagnostics = diagnostics;
            }

            public string Status { get; }
            public ICppDeclaredElement[] Elements { get; }
            public IReadOnlyCollection<CppSemanticDiagnostic> Diagnostics { get; }

            public static TargetResolution Failure(string status, CppSemanticDiagnostic diagnostic)
            {
                return new TargetResolution(status, Array.Empty<ICppDeclaredElement>(), new[] { diagnostic });
            }
        }

        private sealed class SymbolLocations
        {
            public SymbolLocations(CppSourcePosition[] declarations, CppSourcePosition[] definitions)
            {
                Declarations = declarations;
                Definitions = definitions;
            }

            public CppSourcePosition[] Declarations { get; }
            public CppSourcePosition[] Definitions { get; }
        }
    }
}
