# Tool reference

ReSharper MCP Toolset exposes ReSharper C++ semantic queries through Rider's built-in MCP server. The tools are designed around Unreal Engine C++ development: follow a symbol from game code into Rider-known Engine source, inspect its declarations and definitions, trace references, and explore C++ inheritance and overrides.

Nine tools inspect code or run diagnostics. `resharper_cpp_add_existing_file` is the single project-model write operation. None of these tools creates or edits source content, applies Quick Fixes, or starts a project build. Diagnostics uses Rider's normal analysis stages, including its Unreal/UHT stage when applicable.

For installation and connection, see the [README](../README.md). See [Validation](VALIDATION.md) for the tested environment and coverage.

## Choose a starting point

| What you know or need | Tool |
| --- | --- |
| Exact short or qualified C++ name | `resharper_cpp_search_symbols` |
| File, but no symbol name or position | `resharper_cpp_list_symbols_in_file` |
| Identifier position; need identity, declarations, or definitions | `resharper_cpp_inspect_symbol` |
| Direct semantic references to one symbol | `resharper_cpp_find_references` |
| Immediate C++ base types | `resharper_cpp_get_direct_base_types` |
| All Rider-known C++ descendants of a type | `resharper_cpp_find_derived_types` |
| Nearest base members overridden by a function | `resharper_cpp_get_direct_overridden_members` |
| All Rider-known downstream C++ overrides | `resharper_cpp_find_overriding_members` |
| Current ReSharper C++ diagnostics in one file | `resharper_cpp_get_diagnostics` |
| Existing C++ file missing from Rider's project model | `resharper_cpp_add_existing_file` |

Use your client's file or text search to discover unknown spellings. This plugin does not implement fuzzy search or substitute text matches for semantic results. C++ inheritance results do not include Blueprint or other asset hierarchies.

## Shared conventions

### Files, positions, and identity

`file_path` accepts a project-relative or absolute physical path. The file must be available in the current Rider solution's C++ context. Returned source positions use absolute paths and 1-based `line` and `column` values.

Place a position on the identifier itself. Declaration names, definition names, and Rider-resolvable reference/use identifiers are accepted. At a call, member access, or type use, point to the callee, member, or type identifier. Prefer a returned `navigation` or `location` over a guessed coordinate, and obtain a fresh position after edits move the source.

Symbol summaries contain `name`, `qualified_name`, `kind`, declaration and definition counts, and navigation when available. `signature`, `containing_type`, and `ue_module` are included when available. Compare the qualified name and signature when selecting overloads or same-named symbols. `ue_module` identifies a symbol's Unreal module; it is not a module dependency graph.

The core semantic queries report `search_scope: "current_solution_and_rider_known_libraries"`. This can include Engine and library source outside the project directory when Rider knows it. It does not establish complete coverage of every file in an Engine installation. File outlines and diagnostics use the file's primary C++ PSI context, rather than enumerating every possible translation-unit configuration of a shared header.

There is a known external-source context gap in 0.3.17: a file's primary C++ PSI may be available to the outline tool while the document-based position resolver reports `not_indexed / psi_not_available`. Inspection and other position-based tools can therefore fail at a location from an otherwise successful outline. This is not proof that the file is unregistered or wholly unparseable. The release retains the explicit failure; it does not guess a target from the outline or lexical text.

### Paging and time limits

All list tools accept the following optional paging arguments. Inspection and file registration do not use pagination.

| Argument | Default | Valid values |
| --- | --- | --- |
| `offset` | `0` | Integer, at least `0` |
| `max_results` | `100` | Integer, `1` through `200` |

Read `page.has_more`; when it is `true`, repeat the same query with `offset` set to `page.next_offset`. Paging is stateless. Results may change if source, indexing, or Rider settings change between calls; pagination does not hold a snapshot or make a large query cheaper. Diagnostics explicitly reruns the full analysis for each page.

`page.returned_count` is the current page size. `page.total_mapped_count` is included when the tool can report it reliably; an omitted count is not zero. `page.skipped_count` and `diagnostics` describe incomplete mapping or collection. A result can have `status: "ok"` and `has_more: true`: pagination alone is not a partial result.

Every tool accepts optional `timeout_ms`:

| Tools | Default | Valid range |
| --- | --- | --- |
| Inspection, exact search, references, and the four hierarchy tools | `60000` | `1000`–`600000` |
| File diagnostics | `60000` | `1000`–`600000` |
| File outline and existing-file registration | `30000` | `1000`–`120000` |

For the nine query tools, timeouts are tool errors, not a `status: "partial"` response. Reference and diagnostic timeouts cancel the request without returning intermediate results as a complete or pageable set. Raise the limit for intentional large queries. There is no separate cancellation tool. Registration has additional timeout semantics described below because a project-model change may already have committed.

The query wrapper converts only its own timeout into the tool's timeout message. Cancellation from an enclosing caller or another timeout scope is propagated instead of being attributed to the requested tool budget.

### Status and query diagnostics

Normal structured responses use these statuses; not every status applies to every tool.

| Status | Meaning and next step |
| --- | --- |
| `ok` | The operation completed within its stated scope. An empty list can be valid. This is not a compilation verdict. |
| `partial` | Results, locations, metadata, or registration readiness are incomplete. Read `diagnostics` before drawing a conclusion. |
| `ambiguous` | More than one target or project parent was found. Select a returned candidate and retry; the tool does not choose one or merge their results. |
| `not_found` | The requested file, name, position, symbol, or project selection was not found. Read the diagnostic code for the specific reason. |
| `not_indexed` | Required C++ PSI, source registration, or project-model state is unavailable in the current solution context. |
| `unsupported` | The file, target kind, semantic scenario, or registration request cannot be handled by this operation. |

`diagnostics[]` contains query-level `code` and `message` entries. These are distinct from code issues in `get_diagnostics.findings[]`. Invalid arguments, timeouts, and unhandled backend failures are reported as tool errors, rather than successful responses with one of the statuses above.

Query diagnostics provide concise incompleteness or recovery information. Complete results normally have an empty array; internal investigation samples are not part of regular responses.

Optional [unsuccessful call logs](../README.md#unsuccessful-call-logs) can retain non-`ok` responses and tool errors for later review. Recording a status does not classify it as a plugin defect or change its meaning above.

An empty search result does not by itself prove that a symbol does not exist or that a file needs registration. Check the spelling, loaded project, index state, and the returned diagnostics first.

## Inspect a symbol

`resharper_cpp_inspect_symbol`

Required arguments: `file_path`, `line`, `column`. Optional: `timeout_ms`.

The `symbols[]` result contains each candidate's summary, physical `declarations[]` and `definitions[]`, accessibility, and virtual/pure-virtual/final information. Semantic flags such as `is_virtual` are strings, allowing `"unknown"`; clients should not treat them as JSON booleans.

A resolved overloaded call returns the specific overload selected by Rider. `ok` means one C++ target was resolved and its reported locations were mapped; it does not certify that the surrounding source is free of access, argument, or other resolve errors. Use diagnostics to investigate code issues. An ambiguous position returns candidate identities without silently selecting one.

Example arguments, using a placeholder file and identifier position:

```json
{
  "file_path": "Source/MyGame/MyComponent.h",
  "line": 24,
  "column": 10
}
```

The example path and coordinates are illustrative. Replace them with a real indexed file and the identifier position returned by search or an outline.

## Search exact names

`resharper_cpp_search_symbols`

Required argument: `name`, an exact, case-sensitive short name or C++ qualified name. Optional: `kinds` (default `[]`, meaning all kinds), `offset`, `max_results`, `timeout_ms`.

The response contains `symbols[]` with all matching candidates on the requested page. Multiple overloads or scopes are normal search results; this tool does not choose a target for you. A qualified name narrows the scope but can still match multiple overloads.

Template arguments remain part of the exact match: `Box<int>` does not select another specialization or the primary template. A bare short name such as `Box` can return the indexed template family. Prefer a returned `qualified_name` when selecting a particular specialization or operator; a template instantiation that Rider has not independently indexed is not guaranteed to be searchable.

Special name syntax is parsed with Rider's C++ name parser, with compatibility for Rider's returned conversion and literal operator names. An unsupported name representation produces `unsupported` with a diagnostic instead of being reported as absent. Parsing a name does not establish that its target is indexed. Conversion operator searches may still require a larger timeout because they share an index bucket, even when the requested name is qualified.

Unexpected SDK failures remain tool errors with a preparation stage and the original cause; they are not classified as unsupported input. Name parsing uses an in-memory C++ context and does not create a project file.

Search merges occurrences using Rider's linkage-entity equality. If an index entry has no linkage identity, its parser occurrence supplies only a physical identifier position for the same semantic resolver used by inspection. Recovery must produce one valid, non-null canonical linkage entity. The result's qualified name, kind, and metadata come from that entity, and exact-name and kind filters apply to that resolved identity. Parser lexical nesting is not a returned identity, and matching names or locations alone do not merge results.

`unresolved_indexed_symbols` is one combined diagnostic for recovery candidates whose canonical identity could not be established. These entries are omitted, increase `page.skipped_count`, and make the response `partial`; the tool does not choose an arbitrary target when position resolution is ambiguous. `page.total_mapped_count` is omitted when search completeness cannot be established. An omitted total is unknown, not zero; continue paging through available results with `page.has_more` and `page.next_offset`.

These are counts of unresolved index occurrences, not a proven number of missing matches for the requested qualified name. External-source position failures can occur before exact-name filtering, particularly in the shared conversion-operator index bucket. Returning the expected target does not make an otherwise partial search complete or establish that it is the only match.

`kinds` filters normalized names such as `class`, `struct`, `method`, `function`, `field`, `enum`, `namespace`, `concept`, `global_operator`, `member_operator`, `conversion_operator`, or `literal_operator`. Filter strings are trimmed and lowercased; this does not make the symbol name case-insensitive. An empty filter is a useful first query when the kind is uncertain.

```json
{
  "name": "UMyComponent::BeginPlay",
  "kinds": ["method"],
  "max_results": 20
}
```

## Find references

`resharper_cpp_find_references`

Required arguments: `file_path`, `line`, `column`. Optional: `offset`, `max_results`, `timeout_ms`.

`targets[]` identifies the resolved symbol; `references[]` contains physical `location`, source `context`, and `usage_kind`. The current implementation reports `usage_kind: "unknown"`; do not infer read/write/call classifications from it.

References are to the exact semantic target. They do not automatically include references to base or overriding members, and they are not a caller/callee graph. Resolve ambiguity first, then combine references with inspection and source reading to understand how the symbol is used.

If Rider also finds Unreal asset references, the source response remains `partial` and reports the omitted asset-reference count in one diagnostic sentence. Those asset references are not returned as source locations, and the count is not a count of distinct assets.

## Explore C++ inheritance and overrides

All four tools require `file_path`, `line`, and `column`, and accept optional `offset`, `max_results`, and `timeout_ms`. Use the exact type or member identifier appropriate to the query. Responses identify the resolved target in `targets[]`.

| Tool | Required target | Result array and behavior |
| --- | --- | --- |
| `resharper_cpp_get_direct_base_types` | C++ type | `base_types[]`: immediate bases, with symbol identity and available accessibility, virtual, implicit, and pack-expansion information. |
| `resharper_cpp_find_derived_types` | C++ type | `derived_types[]`: Rider-known transitive C++ descendants or implementations. |
| `resharper_cpp_get_direct_overridden_members` | C++ function or method | `overridden_members[]`: nearest base members directly overridden by this member. Multiple inheritance can return more than one. |
| `resharper_cpp_find_overriding_members` | Virtual, pure-virtual, or interface member | `overriding_members[]`: Rider-known transitive downstream C++ overrides or implementations. |

For an upward walk, repeat a direct-base or direct-overridden query at a returned symbol's navigation. Use inspection to move between declarations and definitions.

`get_direct_overridden_members` follows virtual override relationships and excludes non-virtual members hidden by the same name. A function known to have no overridden member returns an empty result. If Rider cannot fully determine the virtual relationships, the response is `partial` and omits a reliable total count; `skipped_count` only counts specific unreadable or unmappable results, so it can remain zero.

If Rider returns an overridden member without attached parser symbols, the plugin uses Rider's resolve-to-linkage semantic identity to recover its global source symbols, then applies the existing physical source mapping. This recovery does not match members by name or text. A member that still cannot be mapped remains an explicitly incomplete result.

One accepted limitation remains: an upstream result for `UActorComponent::EndPlay` can identify the correct member while reporting a different signature, declaration/definition count, or preferred navigation from `inspect_symbol`. Other runs have returned matching summaries; a general fix has not been established. Compare with inspection when those fields are important, rather than treating an `ok` relationship result as proof that all metadata agrees.

Derived and overriding results are flat collections. Their `relation` is `direct` or `indirect` when established by Rider's hierarchy queries. If the direct query is incomplete, unconfirmed relationships are `unknown` and the response is `partial`; confirmed direct relationships remain `direct`. Ordering does not encode ancestry paths or depth. These tools exclude Rider's recognized Unreal asset hierarchy results. Unavailable C++ results or unsupported result shapes are reported as incomplete. Direct-base semantic flags are strings that may be `"unknown"`, rather than guaranteed JSON booleans.

For derived/overriding queries, `page.skipped_count` accounts for unavailable or unmappable results in the recursive result set. Direct-query failures affect relationship certainty separately, so a response can be `partial` with `skipped_count: 0` and a reliable mapped count.

## List symbols in a file

`resharper_cpp_list_symbols_in_file`

Required argument: `file_path`. Optional: `offset`, `max_results`, `timeout_ms`.

`symbols[]` is a source-ordered outline of physical declaration and definition occurrences in one primary C++ PSI context. Each occurrence has a `location`, identity fields, `declaration_role`, `outline_index`, `parent_outline_index`, and `depth`. Parent indexes refer to the full outline, so a parent may be on an earlier page. Pass an occurrence's location to inspection for canonical identity and the full declaration/definition set.

The outline excludes locals, parameters, preprocessor directives, macro definitions, include expansions, and synthetic declarations introduced by macro substitution. Rider qualifier-group nodes are traversed without being counted as declarations or skipped results. A valid indexed file with no declarations covered by the outline can return `ok` with an empty list. Unreadable, unmappable, or truncated structure is reported as `partial` with diagnostics.

```json
{
  "file_path": "Source/MyGame/MyComponent.h",
  "max_results": 100
}
```

## Run file diagnostics

`resharper_cpp_get_diagnostics`

| Argument | Required | Default and meaning |
| --- | --- | --- |
| `file_path` | Yes | One indexed physical C++ file. |
| `line`, `column` | No | Omitted together. If supplied, both must be positive, 1-based integers; use Rider's caret containment, including both range endpoints. An empty range matches its sole position. |
| `min_severity` | No | `"warning"`; accepts `error`, `warning`, `suggestion`, `hint`, or `info`. Uses Rider's effective severity settings. |
| `offset`, `max_results` | No | Shared paging defaults and limits. |
| `timeout_ms` | No | `60000`; range `1000`–`600000`. |

The tool runs a fresh headless ReSharper C++ daemon analysis. It applies Rider's effective settings and normal `VISIBLE_DOCUMENT` stage policy, including Unreal/UHT when Rider considers it applicable. There is no stage-selection argument, and this result is not published into Rider's editor highlighting or solution-wide analysis store. Non-user and generated files can receive different analysis coverage; inspect `source_file` and `daemon` metadata.

`findings[]` contains code diagnostics with effective severity, highlighting type, contributing stage, message and inspection/compiler IDs when available. Each `range` describes characters using an absolute `file_path`, 1-based start/end coordinates, and an exclusive end. The position filter also accepts a caret exactly at that end coordinate; this does not change the returned character range. `findings_metadata` accounts for filtering and skipped or unreadable results. Query-completeness issues remain in the separate `diagnostics[]` array.

Position and severity are filters after the daemon run; they do not restrict the analyzer's work. Pagination runs the full analysis again. `daemon.completion_basis: "do_highlighting_returned"` means the outer daemon call returned, and does not prove every internal stage succeeded. Findings describe Rider's current analysis, not a successful build or a guarantee that all code issues were detected.

```json
{
  "file_path": "Source/MyGame/MyComponent.cpp",
  "min_severity": "warning",
  "max_results": 100
}
```

## Register an existing C++ file

`resharper_cpp_add_existing_file`

This is the only tool that changes Rider's project model. Use it after an external editor or patch has created a file and a file-based semantic query reports `psi_source_not_registered`. A missing global name-search result alone is not evidence that registration is needed.

| Argument | Required | Meaning |
| --- | --- | --- |
| `parent_directory` | Yes | Physical directory already represented by a Rider project or project-folder node. |
| `file_path` | Yes | Existing ordinary C++ source/header file strictly below `parent_directory`. |
| `project_name` | No | Exact, case-sensitive project name; omit initially, then use a returned candidate to resolve ambiguity. |
| `timeout_ms` | No | Default `30000`; range `1000`–`120000`. |

Both paths may be project-relative or absolute. Containment is checked after resolving directory aliases to their physical targets. The tool invokes Rider's Add Existing Item action for one file. It can create project folder/filter nodes for missing physical subdirectories, but does not create, edit, copy, move, link, or delete source content. It accepts neither a directory import nor recursive registration, and does not automatically search for the nearest registered parent.

If the parent belongs to multiple project entities, the response contains `parent_candidates`. Retry with an exact returned `project_name`. If that still cannot identify one parent, the request is unsupported instead of choosing an unstable item ID.

Completed responses report `before` and `after` states for `project_item_registered`, `cpp_psi_source_registered`, `primary_cpp_psi_available`, and `provides_code_model`. `semantic_ready` is true only when all four are true. The `verification_scope` is `project_item_and_primary_cpp_psi`; readiness does not guarantee that every global symbol-name cache is already up to date.

| Outcome | Interpretation |
| --- | --- |
| `not_attempted` | A preflight condition prevented registration; `action_performed` is `none`. |
| `already_registered` | The project item already exists; `action_performed` is `none`. Read `semantic_ready` for current semantic readiness. |
| `registered` | Registration completed and the reported semantic readiness checks passed. |
| `registered_semantic_pending` | Rider accepted registration, but readiness was not fully observed before the deadline; the response is `partial`. |
| `registration_failed` | Rider rejected the operation or returned no usable per-item result; inspect diagnostics and the before/after state. |

Ordinary errors and the tool's own timeout preserve the known registration stage: not attempted, commit result unknown, or Rider accepted/rejected the request before final verification became unavailable. Cancellation propagates unchanged and may provide no final registration state. When a timeout, cancellation, or fault leaves the registration result unavailable, retry with **exactly the same `parent_directory`, `file_path`, and `project_name`**. Registration is idempotent and will not add a duplicate item. Do not infer that no change occurred or choose a different parent solely because the response was unavailable. A missing per-item result also retains the same-argument retry guidance in diagnostics.

Example after the file has already been created by your normal editing workflow:

```json
{
  "parent_directory": "Source/MyGame",
  "file_path": "Source/MyGame/Components/MyNewComponent.h"
}
```

## Compound investigations

Higher-level questions can be investigated by combining the tools with source reading in the client. The table below describes the evidence available for three common questions and the limits of the conclusions it supports.

| Question | Available evidence | Limits |
| --- | --- | --- |
| Who might call this function? | `find_references` locates semantic uses of the selected function. Reading the surrounding source helps distinguish direct calls from other uses and identify their enclosing code. | References have `usage_kind: "unknown"` and no enclosing-function field. Taking a function's address is not itself a call; indirect callers are not automatically recovered. |
| Which functions are called from this function? | Reading the implementation reveals explicit call expressions. `inspect_symbol` at a resolvable callee identifier identifies Rider's selected target or overload. Override queries can expose related implementations. | There is no automatic callee enumeration. These results do not establish which branches execute or which implementation a virtual, function-pointer, delegate, or reflected call reaches at runtime. |
| What code could a change affect? | References, type relationships, and override relationships help identify related code worth reviewing, across game modules, plugins, and Rider-known Engine source. | This is not exhaustive impact analysis. References to base and overriding members require separate queries; `ue_module` reports ownership, not module dependencies. |

The plugin supplies individual semantic facts and mapped source locations. It does not provide a dedicated call graph, data-flow analysis, control-flow analysis, or Blueprint call graph. Conclusions drawn from reading source should be distinguished from the facts returned by Rider, especially when results are partial or paged. The [validation record](VALIDATION.md) identifies the underlying tools and versions exercised; it does not establish that a composed investigation is complete.
