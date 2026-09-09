# Release notes

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
