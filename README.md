# ReSharper MCP Toolset

**ReSharper C++ intelligence for AI-assisted Unreal Engine C++ development.**

ReSharper MCP Toolset brings ReSharper C++ semantic capabilities to AI assistants through JetBrains Rider's built-in MCP server. Designed around Unreal Engine C++ development workflows, it lets an assistant inspect symbols, trace references, explore inheritance and overrides, read declaration outlines, and query C++ diagnostics in the context of your loaded project.

It runs inside Rider and uses the same ReSharper C++ project model and semantic infrastructure that power the IDE. Game modules, GameFeature plugins, Engine source, templates, and Unreal reflection macros shaped its design and validation. This is a Rider plugin; it does not need to be installed in your Unreal project.

## What you can do

| Task | Tools |
| --- | --- |
| Identify a symbol at a declaration, definition, or resolvable use | `resharper_cpp_inspect_symbol` |
| Find exact names and disambiguate overloads or scopes | `resharper_cpp_search_symbols` |
| Trace a symbol's direct semantic references | `resharper_cpp_find_references` |
| Explore base and derived types | `resharper_cpp_get_direct_base_types`, `resharper_cpp_find_derived_types` |
| Follow overridden and overriding members | `resharper_cpp_get_direct_overridden_members`, `resharper_cpp_find_overriding_members` |
| Navigate declarations in a known file | `resharper_cpp_list_symbols_in_file` |
| Read ReSharper C++ diagnostics under the current IDE settings | `resharper_cpp_get_diagnostics` |
| Register an existing C++ file in Rider's project model | `resharper_cpp_add_existing_file` |

Nine tools query code without editing its contents. The tenth, `add_existing_file`, is an explicit project-model write: it registers one existing regular C++ file under a specified project-tree parent. It does not create or edit the file.

Results include semantic identities and navigable physical file locations where available. Queries report ambiguity, incomplete mappings, unsupported cases, and indexing problems explicitly. They never silently substitute a text search for a semantic result.

For parameters, response fields, and troubleshooting details, see the [tool reference](docs/TOOLS.md).

## Requirements

- **Windows and JetBrains Rider 2026.2.1, build `RD-262.9437.287`.** This is the only current build and runtime target. Other Rider builds and operating systems have not been qualified for this release.
- Rider's C++ support and bundled MCP Server plugin enabled.
- A project or solution loaded in Rider, with the relevant C++ files registered and indexed. Engine and library queries require those sources to be known to Rider.
- An MCP client connected to Rider. Runtime validation has used Codex; other clients have not been independently validated by this project.

Unreal Engine C++ development is the primary use case. The tools work with Rider's indexed C++ model, but a broad compatibility claim for other project types is outside the validation described here. These tools do not require Unreal Editor to be running.

## Install and connect

Install the plugin from a ZIP file. Use the ZIP and matching source from the same release, and compare the ZIP's SHA-256 with the checksum supplied with the download. To build it yourself, follow the [build instructions](docs/BUILDING.md). Updates use the same installation procedure.

1. In Rider, open **Settings > Plugins**, then the settings menu and **Install Plugin from Disk**. Select the plugin ZIP and restart Rider. See JetBrains' [plugin installation instructions](https://www.jetbrains.com/help/rider/Managing_Plugins.html#install_plugin_from_disk).
2. Open your Unreal C++ project and allow Rider to load its project model and index the relevant sources.
3. Open **Settings > Tools > MCP Server**, enable the server, and use Rider's client configuration controls or copy the connection configuration into your MCP client. Follow JetBrains' [MCP setup instructions](https://www.jetbrains.com/help/rider/mcp-server.html).
4. Refresh or restart the client so it discovers the ten `resharper_cpp_*` tools. Some clients discover tools only when a new session starts.

Connection addresses and transports come from Rider's configuration. The plugin adds tools to that server and has no separate server process or endpoint to configure.

## Working alongside Rider's other tools

Use `resharper_cpp_*` for C++ identities, references, hierarchies, outlines, and diagnostics. Rider and other integrations can supply complementary asset or editor features. Blueprint asset inspection, running editor interactions, builds, debugging, and refactoring are outside this plugin's tool set.

Review the tools exposed by your Rider MCP configuration to avoid overlapping descriptions and unwanted actions. The scope of these ten tools does not restrict the rest of Rider's MCP server. Hiding a tool from a client's direct list is not a security boundary; this project has not audited every route through Rider's tool router.

## Behavior and limits

- Queries depend on Rider's current project model, PSI, indexes, and settings. An empty result is not proof that a symbol or relationship cannot exist in another context.
- Search accepts exact, case-sensitive names. Reference queries do not automatically merge references to base members or overrides.
- Large Engine queries can take substantial time. Pagination bounds the response size, but some queries still compute the complete result before returning a page. Diagnostics rerun analysis for each page.
- Diagnostic analysis can run Rider's normal Unreal/UHT stage when Rider considers it applicable. The plugin does not expose a build command or directly launch UBT/UHT, and diagnostics are not a substitute for compiling your project.
- Shared headers, generated declarations, and macro expansion can limit physical source mapping. A `partial` response explains missing or skipped results.
- The plugin does not claim zero IDE stalls, exhaustive C++ analysis, or a stable API across Rider releases. Exact version targeting is intentional.

The [validation record](docs/VALIDATION.md) separates current runtime evidence from historical tests and unperformed checks.

## Development and maintenance

This is an independently maintained project by Bathur. **It was developed entirely through vibe coding: AI coding agents generated and iterated on the original plugin implementation.**

Validation combines compilation, JetBrains Plugin Verifier, targeted MCP calls, manual IDE checks, and log review. These checks cover the recorded scenarios; they do not constitute a comprehensive line-by-line human code review, an independent security audit, or exhaustive test coverage. Passing them does not establish that the implementation is free of defects or safe in every project and IDE state.

**Treat this as experimental software and use it at your own risk.** Bugs may produce incorrect or incomplete results, excessive resource use, IDE hangs, or unintended project-model changes. Check important results against the source and Rider before acting on them. Keep project changes recoverable, and deliberately authorize project-model writes such as `add_existing_file`.

The software is provided without warranty under the terms of [LICENSE](LICENSE). Assess its suitability for your workflow; the documented checks and examples are not a guarantee of correctness, security, stability, or freedom from data loss.

Maintenance is driven by the supported UE C++ workflow. No release schedule, response-time commitment, or compatibility guarantee for future Rider versions is promised. When reporting a problem, include the plugin and Rider versions, the tool and parameters, the observed status, and a minimal example you are able to share. Source paths, identifiers, diagnostics, and logs may contain project information; review them before posting.

## License and acknowledgments

Copyright (C) 2026 Bathur. Original project code and documentation are licensed under **GPL-3.0-only with the limited Rider/ReSharper host linking permission** described in [LICENSING.md](LICENSING.md). The unmodified GPL version 3 text is in [LICENSE](LICENSE).

See [third-party notices and acknowledgments](THIRD_PARTY_NOTICES.md) for host components, build dependencies, and the related project that informed the investigation. Rider, ReSharper C++, and Unreal Engine are products of their respective owners. This project is independently developed and is not an official JetBrains or Epic Games product.
