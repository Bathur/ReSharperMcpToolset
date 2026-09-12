# Validation record and limits

This record describes observed development results for a ReSharper C++ plugin designed around Unreal Engine C++ workflows. It separates the Rider versions and plugin revisions actually exercised. Read it together with the [project overview](../README.md), [tool reference](TOOLS.md), and [build instructions](BUILDING.md).

The observations below identify the Rider and plugin revisions tested; coverage is limited to the listed environments and scenarios.

## Evidence by version

| Target and revision | Observed coverage | What it establishes |
| --- | --- | --- |
| Rider 2026.1.5, earlier development baseline | Broad C++/Unreal semantic, diagnostics, cancellation, and existing-file registration matrices in a sandbox | Historical behavior for comparison; not support for that Rider version in the current source |
| Rider 2026.2.1 `RD-262.9437.287`, plugin `0.3.2` | Static checks and a focused runtime regression in the daily Rider installation, including a cold file outline and log inspection | The current underlying semantic and execution-model baseline on the locked target |
| The same Rider build, plugin `0.3.3` | Static checks, installed-version confirmation, and a read-only symbol-position contract matrix | The clarified symbol inspection description matches the observed declaration, definition, and use-site behavior |
| The same Rider build, plugin `0.3.4` | Forced compilation from the exported source, package/configuration checks, Plugin Verifier, and a focused runtime smoke test of the installed Release artifact | Static/package compatibility and the runtime scenarios listed below |
| The same Rider build, plugin `0.3.5` | Static logging checks followed by an installed smoke test | Query behavior and log-file creation were observed; Windows active-file reading failed and the logging acceptance check did not pass |
| The same Rider build, plugin `0.3.6` | Passing separate-process regression and static checks, followed by installed logging, toggle, and timeout-recovery checks | Active logs remain readable, unsuccessful calls are recorded as configured, and toggling logging takes effect without restarting in the tested scenarios |
| The same Rider build, plugin `0.3.7` | Forced exported-source build, isolated consumer-diagnostic regression, and 13 installed calls covering all ten tools | The targeted symbol-inspection, outline, concise-diagnostic, and hierarchy behaviors described below; not a repeat of the full historical matrix |

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

## Interpretation and limits

- Windows and the exact Rider 2026.2.1 build are the current target. Other operating systems, Rider builds, and arbitrary .NET SDK versions have not been validated.
- Measured times are observations from selected local queries, not benchmarks, service-level targets, or performance guarantees. Cold Rider computations may be slow; moving a query off the primary thread does not guarantee an entirely hitch-free IDE.
- Semantic coverage follows Rider's loaded project model, index, PSI contexts, and ability to map physical files. Empty, partial, unsupported, or not-indexed results must be interpreted using the response diagnostics; no text-search fallback is used to simulate semantic success.
- Diagnostics reflect the current Rider settings and daemon decisions. Stage applicability and cached state can affect findings. The normal Rider daemon policy can include an Unreal/UHT stage; the tool does not promise that every internal stage succeeds or expose a stage-disable switch.
- Timeout and recovery samples establish the observed scenarios only. The existing-file registration branch for an unknown commit result after a very short timeout was not reproduced in the historical local tests; its conservative same-parameter retry contract remains documented.
- This record is not a security audit or a promise of ongoing support. The plugin depends on Rider's MCP transport and host process. Nine custom tools provide semantic/diagnostic reads; `add_existing_file` can change the host project model for an existing file.

For a custom build, run the build/static sequence in [BUILDING.md](BUILDING.md), confirm the plugin version loaded by Rider, and exercise representative queries against your project. Record the Rider build, plugin source revision, build configuration, results, and any checks you did not perform. Compilation and Plugin Verifier alone do not establish runtime semantic behavior.
