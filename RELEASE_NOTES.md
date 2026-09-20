# Release notes

## 0.3.10

This release incorporates the changes since the public `0.3.7` release.

- Updates the target to **Windows / Rider 2026.2.2 `RD-262.10315.191`**, with matching minimum and maximum build restrictions. Earlier Rider builds require the corresponding earlier plugin package.
- Corrects search identities recovered from index entries with a null or missing linkage entity. A parser occurrence supplies only a physical identifier position; the existing position resolver must establish one valid, non-null canonical linkage entity before the result is returned.
- Uses that resolved entity for the qualified name, kind, metadata, exact-name filtering, and deduplication. Parser lexical nesting is no longer exposed as a symbol identity or used to match a qualified query.
- Reports `unresolved_indexed_symbols` when a recovery candidate cannot resolve to one canonical entity. These entries increase `page.skipped_count`, keep the response `partial`, and leave `page.total_mapped_count` unknown. Ambiguous candidates are not selected arbitrarily.
- Recovers source symbols for upward override members with no attached parser symbols through Rider's resolve-to-linkage semantic identity and global symbol index. The existing physical source mapping remains in use, with no name or text-search fallback.
- Uses the matching Rider RD model and bundled JBR 25.0.4. Build-tool versions, the ten tool contracts, generated protocol sources, response schema, timeout and threading behavior, and optional unsuccessful-call logging remain unchanged. Temporary semantic sampling code is absent.

Installed checks covered 20 selected calls: recovered search identities, filtering, paging and navigation agreed, false nested qualified names no longer matched, and upstream recovery remained correct. See the [tool reference](docs/TOOLS.md) and [validation record](docs/VALIDATION.md) for scope and limits.

## 0.3.7

- Fixes a null-reference failure in C++ symbol inspection when Rider's index returns an unresolved mapping.
- Corrects file outlines that reported qualifier-group nodes as missing declarations. Members inside those groups remain in the outline; the grouping nodes do not increase `skipped_count` or cause a false `partial` response.
- Keeps query diagnostics concise. Unreal asset references omitted from source results are summarized by count in one sentence, while the response remains `partial`. Other recovery and incompleteness messages are shortened without removing their meaning.
- Corrects hierarchy-result completeness: recognized Unreal asset results remain outside the C++ hierarchy scope, while unavailable C++ results and unsupported shapes are reported as incomplete. Derived/overriding relationships that cannot be confirmed after an incomplete direct query are marked `unknown`.
- Adds isolated regression checks for omission messages, hierarchy-result classification, relation certainty, and qualifier-group child traversal. Installed checks cover all ten tools through 13 selected calls; they do not repeat the full historical semantic or stress matrices.

The ten tools, input parameters, RD model, and exact **Windows / Rider 2026.2.1 `RD-262.9437.287`** target remain unchanged. Optional unsuccessful-call logging remains disabled by default. See the [tool reference](docs/TOOLS.md) and [validation record](docs/VALIDATION.md) for details and coverage.

## 0.3.6

- Adds optional local JSONL logging for non-`ok` responses and tool errors observed by the Kotlin frontend, including timeouts and final cancellations. Ordinary successful calls are not logged. Records support later review; an unsuccessful call is not automatically a plugin defect.
- Adds **Record unsuccessful MCP calls** under **Settings > Advanced Settings > ReSharper MCP Toolset**, disabled by default.
- Stores logs in Rider's log directory with a 32-record queue, a 256 KiB record limit, 8 MiB file rotation, and a 128 MiB retention target. Background writing is best effort: log loss is permitted, and tool execution does not wait for disk writes. Active log files remain readable on Windows while Rider is running.
- Keeps the ten tool contracts, C# query implementation, RD protocol, and exact Rider target unchanged. Exceptions absorbed inside the backend are not collected by this frontend log.
- Adds detached logging checks, including active-file reading and retention protection across separate JVM processes. Installed runtime checks cover logging boundaries, live settings toggles, and a timeout followed by a successful query; they do not repeat the full historical C++ semantic matrix.

See the [logging description](README.md#unsuccessful-call-logs) and [validation record](docs/VALIDATION.md) for scope and evidence.

## 0.3.4

ReSharper MCP Toolset brings ReSharper C++ intelligence to AI assistants through Rider's built-in MCP server. Its workflows are designed around Unreal Engine C++ development, from game and GameFeature modules to Rider-known Engine source.

### Included capabilities

- Ten tools covering symbol inspection, exact symbol search, references, type and override relationships, file outlines, C++ diagnostics, and explicit existing-file registration.
- ReSharper C++ semantic queries across the current solution and Engine/library source known to Rider, with physical locations, ambiguity handling, pagination, and explicit partial results.
- A focus on UE C++ scenarios, including game and GameFeature modules, Engine symbols, templates, and reflection macros.
- Nine tools that do not edit source contents, plus one explicit project-model registration action for an existing regular C++ file.

The tool set builds on the 0.3.3 semantic baseline. See the [tool reference](docs/TOOLS.md) for the contracts and how to combine the tools.

### Target environment

Windows with Rider 2026.2.1, build `RD-262.9437.287`, its C++ support, and its built-in MCP server. Codex is the client used for the recorded runtime validation. See [validation](docs/VALIDATION.md) for the distinction between current and historical evidence.

### Licensing and installation

The project uses GPL-3.0-only with a limited Rider/ReSharper host linking permission. Read [LICENSING.md](LICENSING.md) for the license scope and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependency roles and acknowledgments.

Install the plugin ZIP through Rider's **Install Plugin from Disk** action. Keep the plugin archive, corresponding source version, and download checksum together. The [README](README.md#install-and-connect) covers installation and MCP setup; the [build instructions](docs/BUILDING.md) cover building from source.

### Known limits

The target Rider build is deliberately narrow. Large Engine queries can be expensive, diagnostic pagination reruns analysis, and unmappable semantic results are reported as partial. The plugin does not provide builds, debugging, refactoring, Quick Fixes, a standalone call graph, or running Unreal Editor control. See the [README](README.md#behavior-and-limits) and [tool reference](docs/TOOLS.md).
