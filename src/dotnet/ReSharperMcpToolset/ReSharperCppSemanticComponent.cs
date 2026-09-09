// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using JetBrains.Application.Parts;
using JetBrains.Application.Progress;
using JetBrains.Application.Threading;
using JetBrains.DocumentManagers;
using JetBrains.DocumentModel;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.Rd.Tasks;
using JetBrains.RdBackend.Common.Features;
using JetBrains.ReSharper.Feature.Services.Util;
using JetBrains.ReSharper.Feature.Services.Protocol;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Psi.Impl.Search.Operations;
using JetBrains.ReSharper.Psi.Resolve;
using JetBrains.ReSharper.Psi.Search;
using Bathur.ReSharperMcpToolset.Protocol;
using JetBrains.Util;
using TypedDocColumn = JetBrains.Util.dataStructures.TypedIntrinsics.Int32<JetBrains.DocumentModel.DocColumn>;
using TypedDocLine = JetBrains.Util.dataStructures.TypedIntrinsics.Int32<JetBrains.DocumentModel.DocLine>;

namespace Bathur.ReSharperMcpToolset
{
    [SolutionComponent(Instantiation.ContainerAsyncAnyThreadSafe)]
    public sealed class ReSharperCppSemanticComponent
    {
        public ReSharperCppSemanticComponent(
            Lifetime lifetime,
            ISolution solution,
            IShellLocks shellLocks,
            IPsiServices psiServices,
            DocumentManager documentManager,
            IFinderOperationManager finderOperationManager,
            ICppUE4ModuleNamesProvider ue4ModuleNamesProvider,
            JetBrains.ReSharper.Psi.Cpp.Caches.CppGlobalSymbolCache globalSymbolCache,
            ILogger logger)
        {
            var model = solution.GetProtocolSolution().GetBathurReSharperMcpToolsetModel();
            var engine = new CppSemanticEngine(
                solution,
                psiServices,
                documentManager,
                finderOperationManager,
                ue4ModuleNamesProvider,
                globalSymbolCache,
                logger);
            var diagnosticsEngine = new CppDiagnosticsEngine(
                solution,
                documentManager,
                globalSymbolCache,
                logger);

            model.InspectSymbol.SetAsync((requestLifetime, request) =>
                shellLocks.ExecuteOrQueueReadLockAsync(
                    requestLifetime,
                    "Rider C++ inspect symbol",
                    () => engine.InspectSymbol(requestLifetime, request),
                    nameof(ReSharperCppSemanticComponent),
                    nameof(CppSemanticEngine.InspectSymbol)));

            model.SearchSymbols.SetAsync((requestLifetime, request) =>
                shellLocks.StartReadActionAsync(
                    requestLifetime,
                    readLifetime => engine.SearchSymbols(readLifetime, request)));

            model.ListSymbolsInFile.SetAsync((requestLifetime, request) =>
                shellLocks.StartReadActionAsync(
                    requestLifetime,
                    readLifetime => engine.ListSymbolsInFile(readLifetime, request)));

            model.GetCppFileSemanticState.SetAsync((requestLifetime, request) =>
                shellLocks.ExecuteOrQueueReadLockAsync(
                    requestLifetime,
                    "Rider C++ file semantic state",
                    () => engine.GetCppFileSemanticState(requestLifetime, request),
                    nameof(ReSharperCppSemanticComponent),
                    nameof(CppSemanticEngine.GetCppFileSemanticState)));

            model.GetDiagnostics.SetAsync((requestLifetime, request) =>
                shellLocks.StartReadActionAsync(
                    requestLifetime,
                    readLifetime => diagnosticsEngine.GetDiagnostics(readLifetime, request)));

            model.FindReferences.SetAsync((requestLifetime, request) =>
                ExecuteFindReferencesAsync(
                    requestLifetime,
                    request,
                    shellLocks,
                    engine));

            model.GetDirectBaseTypes.SetAsync((requestLifetime, request) =>
                shellLocks.StartReadActionAsync(
                    requestLifetime,
                    readLifetime => engine.GetDirectBaseTypes(readLifetime, request)));

            model.FindDerivedTypes.SetAsync((requestLifetime, request) =>
                shellLocks.StartReadActionAsync(
                    requestLifetime,
                    readLifetime => engine.FindDerivedTypes(readLifetime, request)));

            model.GetDirectOverriddenMembers.SetAsync((requestLifetime, request) =>
                shellLocks.StartReadActionAsync(
                    requestLifetime,
                    readLifetime => engine.GetDirectOverriddenMembers(readLifetime, request)));

            model.FindOverridingMembers.SetAsync((requestLifetime, request) =>
                shellLocks.StartReadActionAsync(
                    requestLifetime,
                    readLifetime => engine.FindOverridingMembers(readLifetime, request)));
        }

        private static async Task<FindReferencesResponse> ExecuteFindReferencesAsync(
            Lifetime requestLifetime,
            CppPagedTargetRequest request,
            IShellLocks shellLocks,
            CppSemanticEngine engine)
        {
            var execution = await shellLocks.ExecuteOrQueueReadLockAsync(
                    requestLifetime,
                    "Prepare Rider C++ semantic references",
                    () => engine.BeginFindReferences(requestLifetime, request),
                    nameof(ReSharperCppSemanticComponent),
                    nameof(CppSemanticEngine.BeginFindReferences))
                .ConfigureAwait(false);

            if (execution.ImmediateResponse != null)
            {
                return execution.ImmediateResponse;
            }

            await execution.Completion.ConfigureAwait(false);
            requestLifetime.ThrowIfNotAlive();

            return await shellLocks.ExecuteOrQueueReadLockAsync(
                    requestLifetime,
                    "Map Rider C++ semantic references",
                    () => engine.CompleteFindReferences(requestLifetime, execution),
                    nameof(ReSharperCppSemanticComponent),
                    nameof(CppSemanticEngine.CompleteFindReferences))
                .ConfigureAwait(false);
        }
    }
}
