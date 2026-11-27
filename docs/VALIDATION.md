# Validation record and limits

This record describes observed development results for a ReSharper C++ plugin designed around Unreal Engine C++ workflows. It separates the Rider versions and plugin revisions actually exercised. Read it together with the [project overview](../README.md), [tool reference](TOOLS.md), and [build instructions](BUILDING.md).

The observations below identify the Rider and plugin revisions tested; coverage is limited to the listed environments and scenarios.

## Current release 0.3.24

On 2026-11-27, validation used **Rider 2026.3.0.2 on Windows, build `RD-263.7307.355`**. The build and the packaged plugin descriptor both require that exact Rider build.

### Build and installed package

The SDK helper extracted the unique `lib/rd/rider-model.jar` entry from the 2026.3.0.2 archive. Its size, SHA-256 and CRC-32 matched the previous SDK model. Regenerating the protocol produced the same Kotlin and C# source files. [RD model details](BUILDING.md#obtain-the-rd-model-input)

The Kotlin frontend compiled with Rider's JBR 25.0.4.1. The C# backend compiled with .NET SDK 10.0.401 against the installed Rider's ReSharperHost assemblies. Packaging, configuration validation and all five isolated checks passed. Plugin Verifier 1.411 reported **Compatible** for the 405,501-byte plugin ZIP. The installed JAR and DLL matched the package entries by SHA-256. The main backend loaded assembly version `0.3.24.0`.

### Build from the public source snapshot

The 55-file snapshot was built with its own `build.ps1`, the locked RD model and existing dependency caches. All 27 scheduled tasks executed in 7 minutes 31 seconds with `--offline --rerun-tasks --no-build-cache`. All six isolated checks passed, including 57 loopback SDK downloader cases. Plugin Verifier 1.411 reported **Compatible** for `RD-263.7307.355` and downloaded 0 bytes during verification.

The rebuilt ZIP matched the package used for the installed regression byte for byte. Its SHA-256 was `E49B4074658F203F25D75CAA9FA357E722F3F68CC001087606651D7E7AA9C88A`. After the build, all 55 source files matched the inputs, including both generated protocol files.

### Installed Lyra regression

Codex discovered all ten tools. On the indexed Lyra project, seven read-only scenarios and one follow-up inspection used the same inputs and returned the same full responses as the 0.3.23 run. Every call returned `ok` with empty query diagnostics. Every paged result fit on one page with zero skipped items.

| Scenario | Observed result |
| --- | --- |
| Search for `IsExperienceLoaded` and inspect the component method | Two same-name candidates. Inspection confirmed `bool() const`, one declaration and one definition. |
| Direct references | 13 distinct physical locations |
| Component header outline | 37 entries, including the constructor, with the expected nesting |
| Component's direct base types | `UGameStateComponent` and `ILoadingProcessInterface` |
| Component's `EndPlay` override | One direct base method, `UActorComponent::EndPlay`. Its summary again failed the consistency check below. |
| ShooterCore diagnostics | Four warnings in `AimAssistTargetManagerComponent.cpp`. Unused includes at lines 9 and 15, an unused local at line 171, and unreachable code at line 213. |

The hierarchy summary pointed to `ActorComponent.h:949:26`, gave the signature `void(const EEndPlayReason::Type EndPlayReason)` and reported zero declarations and one definition. Inspecting that position returned `ActorComponent.cpp:1664:23`, the signature `void(EEndPlayReason::Type)`, one declaration and one definition. The relationship and symbol identity agreed.

The main backend served all eight calls. Its log recorded no plugin exception or watchdog during the queries. At startup, the lightweight backend twice reported that `ReSharperCppSemanticComponent` could not resolve `ICppUE4ModuleNamesProvider`, as in 0.3.23.

## Previous compatibility release 0.3.23

Version 0.3.23 targets **Windows / Rider 2026.3.0.1 `RD-263.7307.325`**. Both descriptor bounds require this build. The compatibility update changes the target build and the matching RD model lock.

### Static and package checks

On 2026-11-20 the SDK helper extracted the 3,305,134-byte `rider-model.jar` from the official archive using HTTP Range requests. The model's decompressed size, CRC and JAR structure matched the ZIP metadata and expected structure. A second extraction reproduced the locked SHA-256. RD generation produced the same Kotlin and C# protocol sources as 0.3.22. [Model details and build commands](BUILDING.md#obtain-the-rd-model-input)

The plugin compiled against the installed Rider using its JBR 25.0.4.1 and .NET SDK 10.0.401. Packaging, configuration validation and the five isolated checks for consumer diagnostics, SDK symbol names, failure logging, timeout ownership and registration safety passed. Plugin Verifier **1.411** reported **Compatible** for the exact build. The verifier ran offline. The earlier packaging step retrieved a Rider-specific dependency after a TLS retry.

The plugin ZIP was 405,497 bytes. Its descriptor, backend assembly version `0.3.23.0`, 122 Java 25 class files, licenses and source notice were verified. The installed DLL and JAR matched their package entries by SHA-256. The frontend plugin list, main backend log and loaded backend module confirmed that Rider was running 0.3.23.

### Exported-source verification

The 55-file source snapshot was built through its own build entry point with the locked Rider model and existing dependency caches. All 27 tasks executed in 7 minutes 4 seconds with `--offline --rerun-tasks --no-build-cache`. The six isolated checks passed, including all 57 cases in the loopback downloader test. Plugin Verifier 1.411 reported **Compatible** for `RD-263.7307.325` and downloaded 0 bytes during verification.

The rebuilt 405,497-byte ZIP matched the installed and tested package byte for byte. Its SHA-256 was `E629BE18AC062112A931ACCD40E9E7EA962B3C7F4F73DB0C0BEBC6ED5461B23C`. A post-build comparison confirmed that all 55 source files still matched the inputs, including both generated protocol files.

### Installed representative regression

Codex discovered all ten tools. **Six of seven representative scenarios met their expectations.** The upstream override query found the correct member, but its summary failed a consistency check. All seven read-only calls and the additional inspection returned `ok` with empty query-level `diagnostics[]`. All paged responses reported zero skipped results and no further pages.

| Scenario | Observed result |
| --- | --- |
| Exact `IsExperienceLoaded` search | Two methods, one in the component and one in the game mode |
| Inspect the component method | `bool() const`, one declaration and one definition, consistent with search |
| Direct references to the method | 13 distinct physical locations in the indexed project |
| `LyraExperienceManagerComponent.h` outline | 37 entries with the expected nesting, English kinds and constructor |
| Component direct bases | `UGameStateComponent` and `ILoadingProcessInterface` |
| Component `EndPlay` upstream member | One direct `UActorComponent::EndPlay`. The signature, preferred navigation and declaration count differed from direct inspection. |
| ShooterCore C++ diagnostics | Four warnings in `AimAssistTargetManagerComponent.cpp`. Two unused includes at lines 9 and 15, an unused local at line 171 and unreachable code at line 213. The severity filter excluded 1,584 of 1,588 raw findings. |

The extra `inspect_symbol` call used the position returned by the upstream query. The two responses identify the same member.

| `UActorComponent::EndPlay` field | Upstream query | Inspection at the returned position |
| --- | --- | --- |
| Signature | `void(const EEndPlayReason::Type EndPlayReason)` | `void(EEndPlayReason::Type)` |
| Preferred navigation | `ActorComponent.h:949:26` | `ActorComponent.cpp:1664:23` |
| Declarations / definitions | 0 / 1 | 1 / 1 |

The discrepancy also occurred in 0.3.21. The 0.3.22 sample returned the canonical summary. Its cause remains unknown. These runs cover selected read-only queries on an indexed Lyra project. Coverage of other tool contracts and performance cases is recorded below with the versions tested.

### Runtime logs

The main backend served all eight calls. The call window showed no plugin exception or main backend watchdog. A verbose unresolved-symbol message accompanied the upstream query. Rider logged two thread-pool-exhaustion warnings about 122 milliseconds before the first call and restored parallelism during the run.

Earlier in the same session, Rider's lightweight backend twice reported that it could not construct `ReSharperCppSemanticComponent` because the C++ service `ICppUE4ModuleNamesProvider` was unavailable. The same startup error appears in the previous day's lightweight backend log for 0.3.22 on `RD-263.7307.258`.

## Previous compatibility release: 0.3.22

Version 0.3.22 targets **Windows / Rider 2026.3 `RD-263.7307.258`**. It updates the exact Rider bounds and matching RD model while preserving the 0.3.17 semantic implementation, ten tool contracts, RD source and generated files, and build-tool versions. No plugin API adaptation was required.

### Static, source-utility and package checks

On 2026-11-11, forced model verification, RD generation and C#/Kotlin compilation passed using the installed Rider's JBR 25.0.4.1 and .NET SDK 10.0.401. Packaging, configuration validation and all five plugin regression harnesses (`consumerDiagnosticsTest`, `searchNamesTest`, `failureLogTest`, `ownTimeoutTest`, `registrationSafetyTest`) passed. Plugin Verifier **1.411** reported **Compatible** against the single locked local target, with **0 bytes downloaded during verification**. The complete upgrade was not download-free: it retrieved SDK ranges and restored a target-specific compiler helper through the existing build entry point. Exact-build, IDE-layout, test-source and toolchain warnings remain.

The official 2026.3 SDK archive uses ZIP64. The extraction helper now supports single-disk ZIP64 V1 directory records and entry extended fields, retaining bounded Range reads, strong-ETag/cache provenance checks, required expected size/SHA-256 and atomic verified replacement. It rejects multi-disk/V2 layouts and malformed, overflowing or out-of-range metadata. The expanded loopback fixture passed **57 downloader-process cases**, including virtual archives with real offsets above 4 GiB and bounded transferred bytes. The fixture did not allocate or download multi-gigabyte archives. These checks do not establish general ZIP/ZIP64 or every HTTP server/proxy behavior.

The new 3,303,457-byte model identity was established from the official HTTPS archive with strong-ETag, unique-entry, ZIP-header/CRC and JAR checks, then retrieved again through the production helper against the new lock. Its SHA-256 is a locally recorded content identity, not a JetBrains-published digest. The complete Rider archive was not downloaded. RD generation left both generated protocol files unchanged.

Package checks confirmed plugin version 0.3.22, exact Rider bounds, backend 0.3.22.0, all 122 packaged classes targeting Java 25, and the required source/license notices. The ZIP contained no backend PDB, host DLLs or test classes. Installed DLL/JAR hashes matched the verified package; frontend/backend logs and the loaded backend module confirmed this version.

### Exported-source verification

The 55-file public source snapshot was independently built through its own build entry point with `--rerun-tasks --no-build-cache --offline`, using the same installed Rider and .NET SDK 10.0.401. It reused downloaded dependencies through `-CacheRoot` and read the existing locked model; project state and build outputs belonged to the snapshot. All **27 scheduled tasks executed and passed**: model verification, RD generation, C#/Kotlin compilation, packaging, configuration checks, all six isolated harnesses and Plugin Verifier. The downloader harness passed its **57 loopback-only cases**, using an existing Python 3.12.11 runtime selected with the documented option.

Plugin Verifier **1.411** reported **Compatible** against the exact target, with **0 bytes downloaded in this verification run**. The independently rebuilt plugin ZIP was byte-for-byte identical to the installed package exercised below. Export verification confirmed that all 55 source inputs, including regenerated protocol files, remained unchanged after the build. The subsequent documentation-only update and export do not repeat the binary build or installed semantic regression. These checks establish the tested source/package correspondence; they are not an empty-cache first build or a repeat of the live semantic matrix.

### Installed representative regression

Codex discovered all ten custom tools. Seven read-only calls covered the existing representative scenarios. Every call returned `ok`, query-level diagnostics were empty, and all paged responses had no skipped results or additional pages:

| Scenario | Observed result |
| --- | --- |
| Exact `IsExperienceLoaded` search | Two candidates |
| Inspection of selected returned navigation | Canonical identity, signature, navigation and declaration/definition counts matched the search summary |
| Direct references to that member | Thirteen unique mapped locations; this count describes the current project/index state |
| `LyraExperienceManagerComponent.h` outline | 37 items, normalized English kinds, hierarchy, constructor, and no namespace signature |
| Component direct bases | `UGameStateComponent` and `ILoadingProcessInterface` |
| Component EndPlay upstream member | One direct `UActorComponent::EndPlay`, canonical signature and definition navigation, with one declaration and one definition |
| ShooterCore diagnostics | The same four warnings at lines 9, 15, 171 and 213; 1,588 raw highlightings, 1,584 below the severity filter, and no mapping/metadata omissions |

The EndPlay summary matched the canonical expectation in this run, so no additional inspection comparison was made. A single healthy result does not resolve the previously observed state-dependent summary discrepancy or determine its cause. External-source position mapping and conversion-query completeness/performance also remain known limits.

The bounded log review corresponded to all seven calls and the backend reference/diagnostic counts. No explicit plugin API, protocol or wrong-thread failure was observed in that window. Two generic Rider thread-pool-exhaustion warnings and an approximately 0.602-second RD latency/FlushAll watchdog occurred alongside PCH and other host activity. Their cause was not established, and a contribution from plugin/host workloads was not excluded. The results do not establish a stall-free or warning-free IDE session.

The project's existing modified/untracked file state and the hashes of those files were unchanged after the queries. No source files or project items were edited or registered, and no target-project build or PIE session was launched for this regression. These checks do not repeat the full semantic/performance matrix, downstream inheritance/overrides, pagination, deliberate timeout/cancellation, fresh registration or special-name cases.

## Previous compatibility release: 0.3.21

Version 0.3.21 targets **Windows / Rider 2026.2.4.1 `RD-262.11879.119`**. It changes the exact Rider bounds and the matching SDK distribution URI, preserving the 0.3.17 semantic implementation, ten tool contracts, RD source and generated files. The model extracted from the official 2026.2.4.1 SDK matched the previous locked size and SHA-256; additional strong-ETag, entry-CRC and JAR checks passed. The active model did not need replacement.

### Static and package checks

On 2026-11-04, forced model verification, RD generation and C#/Kotlin compilation passed using the installed Rider's JBR 25.0.4.1 and .NET SDK 10.0.401. Packaging, configuration validation and all five plugin regression harnesses (`consumerDiagnosticsTest`, `searchNamesTest`, `failureLogTest`, `ownTimeoutTest`, `registrationSafetyTest`) passed. Plugin Verifier **1.411** reported **Compatible** against the single locked local target; its run downloaded **424.39 MB** of plugins and dependencies. The complete Rider archive was not downloaded. Exact-build, optional IDE-layout, test-source and toolchain warnings remain.

Package checks confirmed plugin version 0.3.21, exact Rider bounds, backend 0.3.21.0, all 122 packaged classes targeting Java 25, and the required source/license notices. The ZIP contained no backend PDB, host DLLs or test classes. Installed DLL/JAR sizes and hashes matched the package; frontend/backend logs and the loaded backend module confirmed this version.

### Exported-source verification

The 55-file public source snapshot was independently built through its own build entry point with `--rerun-tasks --no-build-cache --offline`, using the same installed Rider and .NET SDK 10.0.401. It reused downloaded dependencies through `-CacheRoot` and read the locked model from the existing cache; project state and build outputs belonged to the snapshot. All **27 scheduled tasks executed and passed**: model verification, RD generation, C#/Kotlin compilation, packaging, configuration checks, all six isolated harnesses and Plugin Verifier. The downloader harness passed its **36 loopback-only cases**, using an existing Python 3.12.14 runtime selected with the documented option.

Plugin Verifier **1.411** reported **Compatible** against the exact target, with **0 bytes downloaded in this verification run**. The independently rebuilt plugin ZIP was byte-for-byte identical to the installed package exercised below. Export verification confirmed that all 55 source inputs, including regenerated protocol files, remained unchanged after the build. Only documentation was then updated with these results and re-exported, without another binary rebuild. These checks establish the tested source/package correspondence; they are not an empty-cache first build or a repeat of the live semantic matrix.

### Installed representative regression

Codex discovered all ten custom tools. Eight read-only calls covered seven existing scenarios and one additional EndPlay inspection comparison. Every tool returned `ok`, query-level diagnostics were empty, and all paged responses had no skipped results or additional pages. Semantic expectations were checked separately from those statuses:

| Scenario | Observed result |
| --- | --- |
| Exact `IsExperienceLoaded` search | Two candidates |
| Inspection of the selected returned navigation | Canonical identity, signature, navigation and declaration/definition counts matched the search summary |
| Direct references to that member | Twelve unique mapped locations; three returned calls were in project files added after the earlier nine-reference baseline |
| `LyraExperienceManagerComponent.h` outline | 37 items, normalized English kinds, hierarchy, constructor, and no namespace signature |
| Component direct bases | `UGameStateComponent` and `ILoadingProcessInterface` |
| Component EndPlay upstream member | Correct `UActorComponent::EndPlay` identity and direct relationship; summary consistency failed as described below |
| ShooterCore diagnostics | The same four warnings at lines 9, 15, 171 and 213; 1,588 raw highlightings, 1,584 below the severity filter, and no mapping/metadata omissions |
| Additional inspection of the upstream return location | Canonical `void(EEndPlayReason::Type)`, one declaration and one definition, with the Engine definition as preferred navigation |

The upstream query returned the known EndPlay summary discrepancy: a header navigation, zero declarations/one definition, and a signature containing the parameter name. Inspection at that returned position produced the canonical definition navigation and one declaration/one definition. Signature, navigation and declaration count differed. This reproduces an existing unresolved limitation; a correct relationship and an `ok` status do not establish metadata equivalence. The run does not determine its cause or establish that the Rider update introduced it.

Frontend logs corresponded to all eight calls. The inspected window had no plugin API/thread-access exception, backend error/fatal entry, watchdog or new unsuccessful-call record. A verbose unresolved-symbol entry overlapped the upstream query; it was retained as an observation without assigning a cause. Existing Unreal Editor connection polling, a missing-launcher-file warning and an earlier toolbar event were recorded separately. Project Git status stayed clean, and no source files, project items, build or PIE session were changed or started for this regression.

These checks do not repeat the full semantic/performance matrix, downstream inheritance/overrides, pagination, deliberate timeout/cancellation, fresh registration or special-name cases. External-source position mapping, conversion-query completeness/performance and EndPlay summary consistency remain known limits.

## Previous compatibility release: 0.3.20

Version 0.3.20 targets **Windows / Rider 2026.2.3.1 `RD-262.10968.170`**. It updates the exact Rider bounds and matching RD model while preserving the 0.3.17 semantic implementation, ten tools and protocol source. The toolchain versions remain unchanged.

On 2026-10-02, model verification, forced RD generation and C#/Kotlin compilation passed against the installed target; generated protocol files were unchanged. Packaging, configuration validation and all five plugin regression harnesses (`consumerDiagnosticsTest`, `searchNamesTest`, `failureLogTest`, `ownTimeoutTest`, `registrationSafetyTest`) passed. Plugin Verifier **1.410** reported **Compatible**, with **0 bytes downloaded during verification**. The earlier packaging step restored a missing target-specific compiler helper online, so the complete upgrade was not download-free. Exact-build, optional IDE layout, test-source and toolchain warnings remain.

Package inspection confirmed version 0.3.20, exact build bounds, backend 0.3.20.0, Java 25 bytecode and source/license notices, with no backend PDB, host DLLs or test classes. The local new-model lock was first established from the official HTTPS archive with strong-ETag, entry-size/CRC and JAR checks, then independently retrieved with the unchanged production helper; its hash is not a JetBrains-published digest.

Installed DLL/JAR sizes and hashes matched the verified package. Frontend/backend logs and the loaded backend module confirmed 0.3.20. A direct HTTP MCP initialization and tool listing succeeded and returned all ten custom tools. The existing Codex session had not rediscovered Rider's native MCP tools; this direct connection check does not establish native-client integration or C++ query behavior.

The 2026-10-02 release preparation explicitly skipped C++ semantic regression. The public source was exported from the matching development inputs without another build or regression run. Export and archive checks establish the selected file set and byte correspondence; they are not an independent rebuild of that exported snapshot. A subsequent 2026-10-03 run made seven native read-only calls on the same installed package: two search candidates, matching inspection, nine references, 37 outline items, two bases, a canonical EndPlay upstream member and four existing warnings were observed. All seven returned `ok` with empty query diagnostics and no skipped results. That healthy EndPlay sample did not prove the state-dependent limitation fixed. No full semantic matrix, fresh registration, live cancellation faults or empty-cache source build was claimed for that version.

## Previous accepted baseline: 0.3.17

Its verified target was **Windows / Rider 2026.2.2 `RD-262.10315.191`**. Version 0.3.17 retains ten tools and the existing RD protocol. Its scope includes exact template/operator-name lookup, case-sensitive hierarchy identities, corrected operator/concept classification, virtual-only upstream relationships, physical registration containment, and timeout/error-recovery safeguards. Known source-context and symbol-summary gaps remain documented below.

### Static and isolated checks

The development 0.3.17 package completed RD generation, C#/Kotlin compilation, `consumerDiagnosticsTest`, `searchNamesTest`, packaging and configuration checks. Plugin Verifier **1.410** reported **Compatible** against the locked local Rider build, with **0 bytes** downloaded. The exact-build configuration suggestion and optional IDE classpath/Gradle/JBR warnings were retained; success does not mean a warning-free run.

Package inspection confirmed the plugin/backend versions, the exact build bounds, and the required source/license notices. The installed DLL and JAR hashes matched that package. Temporary diagnostic helpers and their test scripts are not part of the release implementation. Removing investigation code restored the same non-diagnostic plugin inputs; it did not implement an external-source fallback or a new EndPlay fix.

The regression checks have distinct scopes:

| Check | What it exercises | What it does not establish |
| --- | --- | --- |
| `consumerDiagnosticsTest` | Production classification, omission accounting and relationship selection using isolated locked-SDK objects, including operator/concept kinds and virtual-slot selection | Construction of a real C++ inheritance graph or every live PSI state |
| `searchNamesTest` | Production ordinary-name paths, actual SDK name values, index keys, exact positive/negative matching, template/operator compatibility and failure classification | Live fragment parsing or whether a particular symbol is present in Rider's index |
| Existing logging, timeout and registration harnesses | Bounded logging and writer failures; timeout ownership and cancellation; physical-path containment and registration-stage messages | All of these checks rerun as one complete 0.3.17 live-IDE matrix, or real project-model commit faults |

The logging, timeout and registration components had passed their applicable isolated checks during development. Real Windows junction fixtures covered internal/external directory aliases and physical parent identities; this is not evidence of a fresh project registration for every alias shape.

### Exported-source verification

The 55-file public source snapshot was independently built on Windows against the locked Rider installation using .NET SDK 10.0.400. The run used `--rerun-tasks --no-build-cache --offline`, reused downloaded dependency caches and the verified RD model, and executed all 27 scheduled tasks. Model verification, RD generation, C#/Kotlin compilation, all six isolated harnesses listed in the build instructions, packaging, configuration validation and Plugin Verifier passed. The downloader harness repeated its 36 loopback-only cases.

The resulting plugin ZIP was byte-for-byte identical to the installed, accepted 0.3.17 archive. Post-build source verification also confirmed that the exported inputs, including the regenerated RD files, were unchanged. Documentation was then updated with these results and re-exported without another binary rebuild. This validates the tested source/package correspondence; it is not a fresh-machine dependency download test or a repeat of the live C++ semantic matrix. Existing test-source, exact-build, IDE-layout and toolchain warnings were retained.

### Installed name-search acceptance

The 0.3.17 installed acceptance run made **25 read-only calls: 21 search and 4 inspect**. Results were **17 `ok`, 5 expected `not_found`, 2 `partial`, and 1 expected `unsupported`**, with no tool errors or timeouts in that window.

| Case | Observed result |
| --- | --- |
| `TIsSigned<int8>` without a preceding inspection | The correct indexed specialization was returned |
| `TIsContiguousContainer<UE::TSharedString<CharType>>` | The nested qualified template name returned its correct target |
| Other indexed specializations, absent arguments, wrong namespaces and case differences | Positive cases stayed distinct; the selected negative cases returned no result |
| Leading global qualifier | Equivalent to the corresponding query without the leading `::` |
| Bare `TIsSigned` and pagination | Eight indexed family members; pages of 3 + 3 + 2 matched the complete response |
| Ordinary `IsExperienceLoaded` names | Two short-name candidates, one qualified candidate, and no match for the wrong scope |
| Literal operator returned QName, short name and C++ spelling | All three forms returned the same `_PrivateASV` target and response |
| Conversion operator returned QName and `operator bool` spelling | Both returned the same correct `TUniquePtr` target; the overall queries remained partial |
| Inspection of four returned locations | Template, literal and conversion summaries agreed with their corresponding search entries |
| An unterminated template name | Explicit `unsupported_name`; no broadened query or false absence claim |

The two conversion queries reported 47 and 35 unresolved index occurrences in that run. Their complete totals remained unknown. Those counts describe a particular index state, not fixed product behavior or a proven number of missing matches for the qualified request. Returning the expected target did not establish uniqueness or completeness, and the broad conversion-index performance limit was not considered solved.

The unsuccessful-call log contained the eight expected non-`ok` responses, with no additional exception or truncation records. The inspected window had no plugin-attributed API/thread-access errors or backend error/fatal entries. Generic watchdog, toolbar and telemetry events were recorded separately; the observations do not establish zero IDE stalls or a warning-free session.

### Other targeted runtime evidence retained by this release

The changes incorporated since 0.3.10 also received selected installed checks during development:

- Case-distinct C++ hierarchy targets remained separate, including direct/indirect distinctions and pagination.
- Inspection, outline and filtering examples covered operators and concepts alongside ordinary symbol kinds.
- Upstream relationships excluded non-virtual and overloaded-name hiding. Overrides with and without the `override` keyword, mixed multiple inheritance, nearest ancestors, pure virtual members, covariant returns, class-template members and free functions had representative checks.
- Physical-path registration checks rejected an external directory alias and a directory target, and preserved already-registered idempotency. An invalid registration timeout produced an explicit not-attempted error with its original cause.
- Diagnostics position filtering matched the start, interior and end caret positions of a finding, while a position after the end did not match. Returned character ranges remained end-exclusive.
- Deliberately short query timeouts were followed by successful queries. Caller/inner-scope cancellation classification also has isolated helper coverage.

These selected checks were performed on the relevant development implementations; they were not all repeated against the final 0.3.17 package. Fresh file registration, terminal file symlinks, live RD disconnection/commit races, every overload or macro form, and all cancellation states were not exhaustively exercised. Controlled source fixtures were removed and project state restored. No Unreal Engine or game build or PIE session was launched for these checks.

### Known limitations retained in 0.3.17

Some external C++ source files have a valid primary C++ PSI and a successful file outline, yet the document-based position resolver cannot reach that context and returns `not_indexed / psi_not_available`. The gap concerns source-file/project-file mapping, not a blanket exclusion of third-party directories. Searches for game or Engine symbols can encounter such unresolved index entries before name filtering and remain `partial`. No primary-file declaration fallback is implemented in this release; outline success must not be taken as proof that every returned position can be inspected.

A state-dependent summary inconsistency remains for `UActorComponent::EndPlay` in upstream override queries. The correct member identity was returned, but navigation, declaration/definition counts and signature formatting could differ from direct inspection even with `status: "ok"`. Other runs of the same binary produced the expected summary. The cause remains undetermined; neither restarting nor obtaining a later healthy result is treated as a proven fix.

### Source-build download checks

The source utilities were validated independently of the plugin's installed runtime. A loopback-only HTTP fixture exercised **36 downloader-process cases**, including stored/deflate entries, case-sensitive names, relative/absolute paths, valid ranges without Content-Length, range rejection and length mismatches, truncated/stalled bodies, bounded retries, validator changes, cache integrity/resumption and failed expected size/hash checks. Failed verification preserved the original output. No real Rider distribution was downloaded for these tests.

`verifyRiderModel` accepted the existing locked model and rejected a wrong-size fixture. A same-size modified fixture was also rejected during RD classpath resolution, before code generation. The original SDK model and the installed plugin package stayed unchanged. These checks do not claim general ZIP/ZIP64 support, exhaustive ZIP-comment handling, or support for every HTTP server/proxy behavior.

## Historical evidence before 0.3.17

Versions `0.3.8` and `0.3.9` were local development revisions, not public releases. Their evidence is retained to distinguish intermediate results from the installed `0.3.10` acceptance checks. The public `0.3.10` release incorporates the Rider 2026.2.2 compatibility update and the search and upstream-override fixes since `0.3.7`.

| Target and revision | Observed coverage | What it establishes |
| --- | --- | --- |
| Rider 2026.1.5, earlier development baseline | Broad C++/Unreal semantic, diagnostics, cancellation, and existing-file registration matrices in a sandbox | Historical behavior for comparison; not support for that Rider version in the current source |
| Rider 2026.2.1 `RD-262.9437.287`, plugin `0.3.2` | Static checks and a focused runtime regression in the daily Rider installation, including a cold file outline and log inspection | A historical semantic and execution-model baseline for comparison with later targets |
| The same Rider build, plugin `0.3.3` | Static checks, installed-version confirmation, and a read-only symbol-position contract matrix | The clarified symbol inspection description matches the observed declaration, definition, and use-site behavior |
| The same Rider build, plugin `0.3.4` | Forced compilation from the exported source, package/configuration checks, Plugin Verifier, and a focused runtime smoke test of the installed Release artifact | Static/package compatibility and the runtime scenarios listed below |
| The same Rider build, plugin `0.3.5` | Static logging checks followed by an installed smoke test | Query behavior and log-file creation were observed; Windows active-file reading failed and the logging acceptance check did not pass |
| The same Rider build, plugin `0.3.6` | Passing separate-process regression and static checks, followed by installed logging, toggle, and timeout-recovery checks | Active logs remain readable, unsuccessful calls are recorded as configured, and toggling logging takes effect without restarting in the tested scenarios |
| The same Rider build, plugin `0.3.7` | Forced exported-source build, isolated consumer-diagnostic regression, and 13 installed calls covering all ten tools | The targeted symbol-inspection, outline, concise-diagnostic, and hierarchy behaviors described below; not a repeat of the full historical matrix |
| Rider 2026.2.2 `RD-262.10315.191`, plugin `0.3.8` | Static checks, both isolated regression harnesses, Plugin Verifier, installed binary binding, and 14 read-only calls across eight tools | Static and representative runtime compatibility; two previously observed result-completeness problems still reproduce |
| The same Rider build, plugin `0.3.9` | Static checks, installed binary binding, and 20 calls across six tools | Upstream recovery and normal controls passed; search acceptance failed on lexical parser identities |
| The same Rider build, plugin `0.3.10` | Static checks, installed binary binding, and 20 selected calls across six tools | The targeted search identity, filtering, paging/navigation and upstream cases passed; not the full historical matrix |

Version `0.3.5` adds a standalone JVM harness for logging and call observation, run with `build.ps1 failureLogTest`. It does not automate the C++ semantic matrices. Compilation, RD generation, project configuration checks, and Plugin Verifier are build/static checks. The semantic observations below came from deliberately selected runtime queries, GUI comparisons where relevant, and Rider log inspection.

Version `0.3.7` also provides `build.ps1 consumerDiagnosticsTest`. It exercises selected production helpers with isolated objects from the locked Rider SDK, without connecting to a running IDE or replacing tests against live C++ PSI.

## Historical Rider 2026.1.5 coverage

The earlier matrix exercised LyraGame, the ShooterCore GameFeature plugin, Engine/library sources, and representative C++ and Unreal constructs:

- Exact-name search, ambiguity, physical symbol positions, declaration/definition identity, and direct references.
- Direct base types, flattened derived types, direct upstream overrides, and flattened downstream overrides, including paging and relation labels.
- Templates, macros, shared headers, generated/reflected declarations, and physical locations in Engine files outside the game project directory.
- File outlines with source order, nesting, cross-page parent indices, and explicit partial results for unmappable or unreadable structure nodes.
- C++ daemon diagnostics in project, Engine, and third-party library files, with severity filtering, stage information, inspection identity, and selected GUI comparisons.
- Deliberately short timeouts for expensive search, references, hierarchy, outline, and diagnostics requests, followed by lightweight queries to check cancellation and recovery.
- Existing-file registration, new physical subdirectories, ambiguous project targets, idempotent retries, rejected inputs, project-model/PSI readiness, and controlled cleanup.

The registration experiments used temporary, recoverable Lyra files; those probes were removed and the affected working tree and project state were checked afterward. Engine files were kept read-only. These observations did not involve an agent-initiated Unreal Engine or Lyra build, UBT/UHT invocation, or PIE session.

This broader historical matrix was not repeated in full on Rider 2026.2.1. In particular, the newer baseline's registration check used an already registered file; it does not re-establish every earlier fresh-registration scenario on the newer Rider build.

## Rider 2026.2.1 baseline

The `0.3.2` runtime regression observed all ten custom tools through the MCP client and a loaded Lyra project model. Representative results included:

- Exact search for `IsExperienceLoaded` returned two candidates. Inspection preserved canonical identity, declarations/definitions, and the `LyraGame` module; the selected member's references returned the earlier nine-item set.
- `ULyraExperienceManagerComponent` retained its two direct base types and the expected project/Engine/GameFeature module mapping.
- The first cold file-outline query completed in approximately **14.7 seconds**, returning **37** items with English normalized kinds, hierarchy, constructors, no skipped entries, and no inappropriate namespace signature.
- The cold outline ran through the background read-action path. The inspected backend log contained no task-specific `Rider C++ file symbol outline` primary-thread watchdog for that call. This addressed the earlier `0.3.1` observation of about **12.8 seconds** of task-specific primary-thread watchdog reporting.
- Diagnostics for `AimAssistTargetManagerComponent.cpp` reproduced four previously observed warnings, at lines 9, 15, 171, and 213 of the tested file revision.
- Registering an already registered header returned `already_registered`, `action_performed=none`, and `semantic_ready=true`.

The inspected frontend/backend logs confirmed the installed plugin version and showed no error, fatal condition, or wrong-thread failure attributed to the plugin in that validation window. Other Rider log errors existed and were investigated separately; this is not a claim that the complete IDE session was error-free.

## Version 0.3.3 contract checks

Version `0.3.3` changed the Kotlin tool/parameter descriptions for `inspect_symbol` and version metadata. It did not change the C# semantic backend, RD protocol, generated models, result schema, statuses, or semantic execution path.

After installation and Rider restart, the MCP client discovered all ten custom tools and the updated descriptions. The read-only checks observed:

- The same member inspected at its header declaration, `.cpp` definition, and a member-call identifier returned `ok`, the same `bool() const` signature, and the same declaration/definition locations.
- The member's references reproduced the nine-item baseline.
- A type-use identifier resolved to the expected canonical type. Its reference query reported 51 mapped entries; the inspection session consumed the first page for coordinate discovery.
- An exact `FindComponentByClass` search exposed four template/non-template candidates across `AActor` and `ACharacter`. A concrete templated call resolved specifically to `AActor::FindComponentByClass`, with the observed `T*() const` signature.

These results support identifier positions at declarations, definitions, and Rider-resolvable references/uses, including a tested overload selection. They do not prove that every expression or erroneous C++ reference resolves. An `ok` inspection status means the implementation obtained a unique C++ target; it does not certify that the source position has no compile or resolve diagnostic.

Both the frontend and backend confirmed loading `0.3.3`, and the inspected plugin-related log entries had no error, fatal, exception, or wrong-thread failure. The `0.3.2` matrix remains the underlying semantic/performance baseline.

## Existing static/package evidence

For the development `0.3.3` source, RD generation, C# compilation, Kotlin compilation, `buildPlugin`, and `verifyPluginProjectConfiguration` completed successfully. Plugin Verifier **1.410** reported **Compatible** against **`RD-262.9437.287`**. The inspected package contained the expected `0.3.3` metadata and the updated tool and column-parameter descriptions.

The tested `0.3.3` ZIP used a Debug backend and contained a PDB. This packaging detail identifies the artifact covered by the recorded static checks.

## Version 0.3.4 exported-source checks

The exported source completed RD generation and C#/Kotlin compilation with `--rerun-tasks --no-build-cache`, using existing dependency caches offline. The compilation tasks executed instead of reusing cached task outputs. `buildPlugin` and `verifyPluginProjectConfiguration` passed. Plugin Verifier **1.410** reported **Compatible** for `0.3.4` against **`RD-262.9437.287`**, with **0 bytes** downloaded.

Configuration validation recommended removing `until-build`; the project deliberately retains that bound to restrict compatibility to the exact Rider build. Plugin Verifier also reported missing optional classpath entries in the local IDE installation. These successful checks were not warning-free.

Package inspection confirmed version `0.3.4`, Java 25 bytecode, the required licenses/notices, and matching `SOURCE_INFO.txt`. The archive contained no backend PDB or Rider host assemblies. The Release DLL's assembly/file version was `0.3.4.0` and its informational version was `0.3.4`, without a Git revision suffix. Its PE debug directory contained only a `Reproducible` entry, with no CodeView or embedded portable PDB. Checks for local development, Rider installation, and user paths found no matches.

## Version 0.3.4 installed runtime smoke test

The Release ZIP was installed in the daily Rider installation. After Rider and the MCP client restarted and project indexing was ready, the frontend and main backend logs confirmed `0.3.4`. The installed DLL and JAR SHA-256 hashes matched the corresponding entries in the verified plugin ZIP, binding this runtime check to that Release artifact.

The client discovered all ten custom tools. The smoke test made ten read-only calls across seven tool types:

- Exact search for `IsExperienceLoaded` returned two candidates.
- Inspection at the selected member's declaration, definition, and member-call identifier returned the same semantic identity and declaration/definition locations.
- References paged as three plus six entries produced nine unique results, with no unmapped, unsupported, or lost-target entries.
- The file outline returned 37 items with English normalized kinds and no namespace signature.
- The direct base-type query returned two types: `UGameStateComponent` and `ILoadingProcessInterface`.
- Direct overridden-member lookup for `EndPlay` returned `UActorComponent::EndPlay`.
- Diagnostics for ShooterCore's `AimAssistTargetManagerComponent.cpp` returned the four warning findings at lines 9, 15, 171, and 213 of the tested file revision. A subsequent inspection completed successfully.

Every call returned `ok` with an empty shared `diagnostics[]` array. The inspected frontend/backend window contained no plugin-attributed error, fatal condition, exception, wrong-thread failure, or long primary-thread watchdog naming a plugin task. Generic `RdDispatcher::FlushAll` watchdogs of roughly one second and caret-animation warnings were also observed; the logs did not establish that the plugin caused them.

Lyra's Git working tree was clean in checks during and after the smoke test. No registration tool was invoked, no source files were edited, and no Unreal Engine or Lyra build was launched. This focused check did not repeat fresh-file registration, complete derived/overriding-member queries, deliberate timeout recovery, or stress matrices. The broader historical observations remain separately identified above. Dependency restoration on a fresh machine with empty caches was not exercised.

## Version 0.3.5 logging checks

On 2026-09-10, the development source completed C#/Kotlin compilation, `failureLogTest`, `buildPlugin`, and `verifyPluginProjectConfiguration`. The RD model and generated sources were unchanged. Plugin Verifier **1.410** reported **Compatible** against **`RD-262.9437.287`**, with no internal API usage findings and **0 bytes** downloaded. The known exact-build configuration suggestion and missing optional IDE classpath warnings remain.

The JVM harness checked detached and bounded snapshots, Unicode/control-character escaping within the 256 KiB JSONL limit, preservation of parameter names after large values, full-queue rejection, non-waiting shutdown, filesystem and logger-internal failures, complete-record rotation, and protection of active/unrelated files during cleanup. Observation checks preserved the original result or thrown object, skipped ordinary successful calls, and isolated failures in the logging callback.

The inspected ZIP contains version `0.3.5`, the default-disabled advanced setting and its text resources, and the logging classes. Test classes and backend PDB files are absent. The ten tool descriptions, parameters, default values, and response construction remain unchanged, as do the C# query source and RD protocol.

These checks did not launch Rider, install into the daily IDE, invoke a C++ tool against a loaded project, or test the settings UI and persistence. Active-file protection was tested with an exclusive lock in the same JVM, not with a second IDE process. No IDE latency or resource-consumption benchmark was performed. The existing `0.3.4` installed runtime evidence remains separate from these logging checks.

## Version 0.3.5 installed logging smoke test

The installed JAR and DLL matched the default-disabled `0.3.5` candidate byte for byte. With logging enabled, exact search and inspection returned the known symbol results and did not create a log directory. A missing-file query returned `not_found` and created a log; a negative line number preserved the original validation error; a following valid inspection succeeded. These five calls did not edit or register source files, and the Lyra working tree was clean afterward.

External reads of the active JSONL file failed twice with Windows lock error 33. The writer's whole-file exclusive lock prevented readers in other processes from accessing the data. Log contents and exact record counts could therefore not be accepted. The settings toggle/persistence and timeout matrix were deferred while fixing this defect in `0.3.6`.

No plugin-attributed error or wrong-thread failure appeared in the inspected native-log window. Generic backend watchdogs overlapped a garbage collection pause; this does not establish zero IDE stalls or negate the log-reading defect.

## Version 0.3.6 active-log reading fix

A new separate-JVM test reproduced the Windows read failure with the old whole-file lock. After moving writer/cleanup locking to a single byte outside the data range, two separate JVMs read and parsed the active log and ran retention cleanup without deleting it. Appending another record remained readable, exact file sizes showed no extension from the lock, and closing released the lock. Protection of a file held by the older whole-file lock also passed.

The full logging/observation harness, C#/Kotlin compilation, packaging, and configuration checks passed. Plugin Verifier **1.410** reported **Compatible** for `0.3.6` against **`RD-262.9437.287`**, with **0 bytes** downloaded. The inspected ZIP retains logging disabled by default and excludes test classes. The C# source, RD protocol, tool entry points, and logging observation service are unchanged; the production logic change is confined to the writer/cleanup lock range.

## Version 0.3.6 installed logging checks

The daily Rider installation's JAR and DLL matched the `0.3.6` candidate byte for byte. With logging enabled, external file inspection could read the active JSONL file while Rider continued running and adding records. The old `0.3.5` file became readable after restart and contained the two expected earlier failure records.

Eleven read-only MCP calls exercised the logging boundary: four ordinary `ok` results produced no records; five unsuccessful calls with logging enabled each produced one record; two unsuccessful calls with logging disabled produced none. The enabled samples included a missing file, an invalid line number, a `partial` exact-name search, and a deliberately short file-outline timeout. The timeout returned its original error after approximately one second, and a following inspection succeeded. Record versions, parameters, diagnostic summaries, and result counts matched the observed calls.

The user disabled logging in Advanced Settings and applied the change; two negative calls preserved their original results while the file stayed at four records. After logging was re-enabled, a new missing-file query increased the count to five, and a successful inspection left it at five. No IDE restart was needed for either toggle. The saved settings file contained `true` at the final check; an additional restart to verify persistence after each toggle was not performed.

The first cold inspection took approximately 7.9 seconds and produced a task-specific primary-thread watchdog while Rider prepared a C++ forced-include snapshot. This happened before the first failure created the logging writer. The observation is retained as a semantic-query startup limitation, not attributed to log-file I/O. Subsequent inspected call windows showed no logging-module failure; the final re-enable window had no new frontend/backend/protocol errors or warnings. This does not establish a warning-free IDE session or a latency guarantee.

The Lyra working tree remained clean; no source was edited or registered and no project build was launched. Disk failures, queue saturation, large-record limits, and retention behavior remain covered by the detached JVM harness rather than fault injection into the daily IDE. Live client-initiated cancellation and the full C++ semantic matrix were not repeated during this logging check.

## Version 0.3.6 exported-source checks

The 44-file public source snapshot completed RD generation, C#/Kotlin compilation, the logging/observation harness, packaging, configuration checks, and Plugin Verifier with `--rerun-tasks --no-build-cache`. All 21 scheduled tasks executed. Downloaded dependencies were reused through the supported cache-root option; this was not an empty-cache or new-machine test.

Plugin Verifier **1.410** reported **Compatible** against **`RD-262.9437.287`**, with **0 bytes** downloaded. The rebuilt plugin ZIP was byte-for-byte identical to the artifact used for the installed `0.3.6` logging checks above. The exporter also verified that generated files and all other public inputs still matched its manifest after the build. The subsequent documentation update did not change plugin binaries.

## Version 0.3.7 fixes and installed checks

The Release artifact completed C#/Kotlin compilation, the logging/observation harness, consumer-diagnostic regression, packaging, and configuration checks. Plugin Verifier **1.410** reported **Compatible** against **`RD-262.9437.287`**, with **0 bytes** downloaded. The RD model and generated sources were unchanged.

The isolated checks cover concise asset/unsupported-reference messages, direct/indirect/unknown relation decisions, counting unavailable C++ and unknown hierarchy results, and visiting children of qualifier groups without counting the groups as missing declarations. They do not simulate every index state or exercise the entire query pipeline inside Rider.

After installation and Rider restart, the loaded JAR and DLL matched the release artifact's entries by SHA-256. Thirteen calls covered all ten custom tools against the indexed Lyra-based project and Rider-known Engine source:

- The `LyraCharacter.cpp` outline retained 54 physical declarations with `ok`, no skipped entries, and no query diagnostics. The earlier two skipped items were Rider qualifier groups, not physical declarations.
- A normal header retained 37 outline entries. A page beginning at offset 20 matched the final 17 entries of the complete result, including a parent index from the preceding page.
- `IsDeadOrDying` references retained one mapped C++ use and reported seven omitted Unreal asset reference results through one short diagnostic. The response remained `partial`; the count does not mean seven distinct assets.
- Exact search for `IsExperienceLoaded` returned two candidates. Direct bases returned two types, and direct overridden-member lookup returned one base member.
- Representative derived and overriding queries mapped 10 and 48 C++ results in the current index, with direct/indirect labels and no query diagnostics. These counts describe that project/index state, not a guarantee across revisions.
- The two use-site inspections that had previously faulted resolved `FTimerManager::Tick` and `APlayerController::ConsoleCommand`, with declaration and definition locations.
- Source diagnostics reproduced four warning findings at lines 9, 15, 171, and 213 of the tested file revision, with an empty query-diagnostics array.
- The registration tool returned `already_registered`, `action_performed=none`, and `semantic_ready=true` for an existing project header; no project-model modification was performed.

Twelve calls returned `ok` with `diagnostics: []`; the single expected `partial` call recorded only the concise asset-omission message in the active log. No internal investigation samples or tool exceptions appeared. The inspected frontend/backend window showed no plugin-attributed error, fatal condition, null-reference exception, or wrong-thread failure. Unrelated Rider asset-cache errors were present, so this does not establish an error-free IDE session.

Unavailable/unknown hierarchy-result branches and the `unknown` relation fallback remain covered by isolated regression rather than deliberate corruption of the daily IDE's index. The original null-reference failure was state-dependent and did not reproduce in every pre-fix query; successful installed samples do not establish coverage of all cold or unresolved states. This check did not repeat fresh-file registration, deliberate timeout/cancellation, the full historical semantic matrices, or latency benchmarks. No source files were edited and no project build was launched.

## Version 0.3.7 exported-source checks

The 45-file public source snapshot completed RD generation, C#/Kotlin compilation, both regression harnesses, packaging, configuration checks, and Plugin Verifier with `--rerun-tasks --no-build-cache`. All 22 scheduled tasks executed. Existing downloaded dependencies were reused offline through the supported cache-root option; only the verified RD model JAR and local Rider configuration were added to the snapshot's ignored inputs.

Plugin Verifier **1.410** reported **Compatible** against **`RD-262.9437.287`**, with **0 bytes** downloaded. The rebuilt plugin ZIP was byte-for-byte identical to the artifact used in the installed `0.3.7` checks above. Exporter verification confirmed that generated files and the other public inputs still matched the manifest after the build. Adding this validation record afterward did not change plugin binaries. This was not an empty-cache or new-machine test.

## Version 0.3.8 Rider 2026.2.2 compatibility checks

The source now targets Windows / Rider 2026.2.2, build **`RD-262.10315.191`**. The matching Rider model was used with the existing RD generator; both generated protocol files remained unchanged. C# and Kotlin compilation succeeded against the new installation without changes to the semantic backend or tool implementation. Java/Kotlin still target Java 25, using the installation's JBR 25.0.4; the remaining build-tool versions were retained.

The logging/observation harness, consumer-diagnostic regression, packaging, and configuration checks passed. Plugin Verifier **1.410** reported **Compatible**, using the local Rider installation and downloading **423.27 MB** of plugins and dependencies. The known exact-build configuration suggestion and missing optional IDE classpath warnings remain. An initial sandbox permission failure and an offline miss for the new compiler helper were resolved through the existing build entry point; they were not API compilation failures.

Package inspection confirmed plugin version `0.3.8`, identical minimum/maximum build bounds of `262.10315.191`, Java 25 bytecode, backend assembly version `0.3.8.0`, and the required license/source notices. No backend PDB, Rider host assemblies, or test classes were included.

## Version 0.3.8 installed runtime checks

After the user installed the candidate, restarted Rider and the MCP client, and reported indexing complete, the installed DLL/JAR hashes matched the verified package. Frontend and backend logs confirmed plugin `0.3.8` and Rider `RD-262.10315.191`; the previous unreachable MCP connection was restored.

Fourteen read-only calls covered eight tools: ten returned `ok` and four returned `partial`. Ordinary checks retained two `IsExperienceLoaded` candidates, the selected member's declaration/definition identity, nine references, 37 header-outline occurrences, two direct base types, the normal `EndPlay` upstream member, and the four existing source warnings at lines 9, 15, 171, and 213. Successful responses had empty query diagnostics.

The two previously observed completeness problems still reproduced. Searches for `ULyraHealthSet` and class-filtered `UAbilityTask` each reported one unreadable indexed item. Direct upstream queries from both the definition and declaration of `ULyraDamageExecution::Execute_Implementation` returned no mapped member and one skipped member. In contrast, inspecting the physical base definition succeeded, and a downstream query from that identity returned both Damage and Heal implementations as direct overrides. This narrows the investigation to the upstream result path but does not establish the root cause or constitute a fix.

The four partial calls each produced exactly one failure-log record, with no additional tool errors, timeouts, or cancellations. No plugin-attributed loading/API errors or wrong-thread failures appeared in the inspected frontend/backend/protocol window. A roughly 0.765-second generic `RdDispatcher::FlushAll` watchdog overlapped the successful diagnostics call; available evidence did not establish plugin causation. Existing asset-cache and toolbar errors occurred before the call window and were not attributed to these queries.

This was a representative compatibility check, not the full historical semantic or performance matrix. It did not repeat derived-type lookup, registration, deliberate timeout/cancellation, complete pagination, or GUI comparison. No source files were edited or registered, and no target-project build was launched. The compatibility change did not fix the two result-completeness issues; earlier installed results remain attributed to Rider 2026.2.1.

## Version 0.3.9 semantic-identity fix checks

The backend compiled against the same locked Rider installation. Packaging, configuration checks, the existing consumer-diagnostic and logging/observation harnesses passed. Plugin Verifier **1.410** reported **Compatible**, with **0 bytes** downloaded. RD model and generated files were unchanged; the Kotlin tool implementation, tool schemas, timeout and execution models were retained.

Code review checked preservation of original index keys, semantic entity/parser deduplication, parser metadata, and the distinction between skipped results and returned but unmerged source occurrences. Such occurrences retain `partial` and do not claim a known complete total. The upstream recovery uses Rider's resolve/linkage identity and existing source mapping, with no name or text matching. Temporary sampling and its test fixtures were removed; package inspection confirmed that the temporary helper type is absent and that the plugin/backend versions are `0.3.9`/`0.3.9.0`.

The user then installed 0.3.9 and restarted Rider/the MCP client. Installed hashes matched the package. Twenty calls across six tools returned 15 `ok` and five search `partial` responses. Upstream queries from both the Damage declaration and definition returned the correct Engine base implementation with one direct result and no omissions; reverse overrides, ordinary symbol inspection, nine references, a 37-item outline, and the normal EndPlay upstream result remained correct.

Search acceptance failed. The raw-parser fallback returned elaborated type uses with lexical containing-class names such as `ALyraCharacterWithAbilities::ULyraHealthSet`, although inspection at all six recovered use positions resolved to the proper global types. A search for the false qualified name also returned that lexical candidate. Two small pages matched the full four-item Health search, but paging consistency and the partial warning did not make the incorrect identity acceptable. Five failure-log records matched the search calls, with no new plugin errors or temporary probes in the call window.

## Version 0.3.10 search position-recovery checks

The follow-up uses the indexed occurrence's existing physical identifier position with the same unique-target PSI resolver as inspection. Only a valid, non-null canonical linkage target reaches name/kind filtering and output. Unmapped, ambiguous, unreadable, or non-canonical occurrences remain explicitly skipped/partial; raw lexical parser metadata is no longer returned as target identity. The upstream fix is unchanged.

Compilation, packaging, configuration checks and both existing regression harnesses passed. Plugin Verifier **1.410** reported **Compatible** against the same locked Rider build, downloading **0 bytes**. Package inspection confirmed versions 0.3.10/0.3.10.0 and the absence of temporary probe, host assemblies, PDB and test classes. RD and Kotlin tool sources remained unchanged.

## Version 0.3.10 installed acceptance

After installation and Rider/client restart, the installed DLL/JAR hashes matched the verified package and frontend/backend logs confirmed 0.3.10. Twenty read-only calls across six tools returned 17 `ok` and three expected `not_found` responses, with no partial responses, skipped results, or query diagnostics.

Unfiltered searches for `ULyraHealthSet` and `UAbilityTask` each returned exactly the constructor and global class; class filtering returned one result each, with correct totals. The three previously false nested qualified names returned no results. Two one-item Health pages matched the complete result and had correct continuation fields. Inspection at all four returned navigations produced summaries identical to the search entries, including identity, kind, signature and declaration/definition counts.

Damage upstream queries from the declaration and definition agreed on the precise Engine base implementation, with one direct result and no omissions. The reverse query retained both Damage and Heal direct overrides. Normal controls retained two IsExperienceLoaded candidates, one EndPlay upstream member, nine references and 37 outline items.

Only the three expected negative searches were added to the unsuccessful-call log. No new plugin-attributed errors, API loading/thread-access exceptions or temporary probes appeared in the inspected window. A generic 0.818-second FlushAll watchdog and missing-token telemetry warnings were observed; these were not established as plugin-caused failures. Existing asset-cache errors occurred before the query window. This does not claim a warning-free IDE or a latency guarantee.

The two targeted fixes passed these installed cases. The run did not repeat the entire historical semantic/performance matrix, deliberately create unknown/ambiguous index states, or repeat cancellation, diagnostics, registration and every hierarchy branch. Conservative partial-result behavior remains in place for unverified states; no project source was edited or built.

## Version 0.3.10 exported-source checks

The 45-file public source snapshot completed RD generation, C#/Kotlin compilation, both regression harnesses, packaging, configuration checks and Plugin Verifier through its own build entry point. The run used `--rerun-tasks --no-build-cache --offline` with in-process Kotlin compilation; all 22 scheduled tasks executed. Existing downloaded dependencies were reused through the supported cache-root option, while project state and outputs belonged to the snapshot.

Plugin Verifier **1.410** reported **Compatible** against **`RD-262.10315.191`**, with **0 bytes** downloaded. The rebuilt plugin ZIP was byte-for-byte identical to the artifact covered by the installed 0.3.10 checks above. Exporter verification confirmed that all 45 source inputs, including the regenerated protocol files, still matched after the build. This record was then added to the source documentation without changing plugin binaries. The check did not exercise a new machine or empty dependency caches.

## Interpretation and limits

- Version 0.3.24 targets Rider 2026.3.0.2 on Windows (`RD-263.7307.355`). The installed sample covers seven read-only scenarios and a follow-up inspection of the `EndPlay` summary. Other coverage applies to the Rider and plugin versions recorded with each run.
- Measured times are observations from selected local queries, not benchmarks, service-level targets, or performance guarantees. Cold Rider computations may be slow; moving a query off the primary thread does not guarantee an entirely hitch-free IDE.
- Semantic coverage follows Rider's loaded project model, index, PSI contexts, and ability to map physical files. Empty, partial, unsupported, or not-indexed results must be interpreted using the response diagnostics; no text-search fallback is used to simulate semantic success.
- An available primary-file context does not guarantee that position-based queries can reach it. The external-source routing gap and EndPlay summary inconsistency described for 0.3.17 remain unresolved; a successful status alone is not proof that all metadata or result sets are complete.
- Diagnostics reflect the current Rider settings and daemon decisions. Stage applicability and cached state can affect findings. The normal Rider daemon policy can include an Unreal/UHT stage; the tool does not promise that every internal stage succeeds or expose a stage-disable switch.
- Timeout and recovery samples establish the observed scenarios only. The existing-file registration branch for an unknown commit result after a very short timeout was not reproduced in the historical local tests; its conservative same-parameter retry contract remains documented.
- This record is not a security audit or a promise of ongoing support. The plugin depends on Rider's MCP transport and host process. Nine custom tools provide semantic/diagnostic reads; `add_existing_file` can change the host project model for an existing file.

For a custom build, run the build/static sequence in [BUILDING.md](BUILDING.md), confirm the plugin version loaded by Rider, and exercise representative queries against your project. Record the Rider build, plugin source revision, build configuration, results, and any checks you did not perform. Compilation and Plugin Verifier alone do not establish runtime semantic behavior.
