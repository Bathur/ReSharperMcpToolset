# Building from source

This plugin exposes ReSharper C++ services through Rider's built-in MCP server. It is designed around Unreal Engine C++ workflows, while its semantic queries operate on C++ files known to Rider. See the [project overview](../README.md), [tool reference](TOOLS.md), and [validation record](VALIDATION.md).

## Requirements

- Windows. The build entry point currently selects Rider's `jbr/bin/java.exe` and uses Windows paths for the backend. Other operating systems have not been validated.
- PowerShell 7, available as `pwsh`.
- An installed JetBrains Rider **2026.2.2**, build **`RD-262.10315.191`**. The build checks the installation's `build.txt` and rejects a different build.
- The JBR/JDK supplied with that Rider installation. The verified installation uses JBR **25.0.4+1-b508.27**; Java and Kotlin compilation target Java **25**.
- A .NET SDK capable of building the SDK-style `net472` backend, with .NET Framework 4.7.2 reference assemblies available to MSBuild. The SDK is not pinned with `global.json`; no minimum SDK version has been established.
- Network access for the initial Gradle, Maven, NuGet, RD model, and Plugin Verifier dependencies. Later runs can reuse local caches; a fully offline first build is not supported.

The currently recorded build inputs are:

| Input | Version or source |
| --- | --- |
| Gradle Wrapper bootstrap | `8.13`, the included Wrapper JAR |
| Gradle distribution | `9.1.0`, URL and SHA-256 recorded in `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin | `2.3.20` |
| RD generator dependency | `2026.1.3` |
| IntelliJ Platform Gradle Plugin | `2.18.1` |
| ReSharper backend references | The locked Rider installation's `lib/ReSharperHost` assemblies |
| Backend target framework | `net472` |
| Protocol model input | The matching Rider `rider-model.jar`, pinned by `rider-model.lock.json` and obtained below |

These are the recorded versions for the existing build. Do not infer compatibility with a newer Rider build or replace the toolchain versions as part of a routine build.

## First-time setup and dependency restore

Prepare these tools before running the build:

1. Install [PowerShell 7 for Windows](https://learn.microsoft.com/en-us/powershell/scripting/install/installing-powershell-on-windows) so `pwsh` is available.
2. Install Rider **2026.2.2**, build **`RD-262.10315.191`**, from JetBrains' [other Rider versions](https://www.jetbrains.com/rider/download/other/). That installation supplies the JBR/JDK and ReSharperHost assemblies used by this build.
3. Install the [.NET SDK for Windows](https://learn.microsoft.com/en-us/dotnet/core/install/windows), rather than only a .NET runtime, so the `dotnet` command and MSBuild are available.

A matching installed .NET Framework 4.7.2 targeting pack supplies the reference assemblies when available. For an SDK-style project such as this one, the .NET SDK can instead restore `Microsoft.NETFramework.ReferenceAssemblies` implicitly through NuGet when those assemblies are missing. This does not require a Visual Studio installation. See Microsoft's [reference assemblies guidance](https://learn.microsoft.com/en-us/dotnet/framework/migration-guide/reference-assemblies). The project does not establish a minimum SDK version for this behavior.

After configuring Rider and obtaining the RD model as described below, the normal build commands handle dependency restoration:

- The included Gradle Wrapper downloads Gradle **9.1.0** using the URL and SHA-256 in `gradle/wrapper/gradle-wrapper.properties` when it is absent from the cache.
- Gradle resolves Kotlin, RD generator, and build-plugin dependencies from the configured repositories. A separate Maven installation is not needed.
- `compileDotNet` invokes `dotnet msbuild` with `/t:Restore;Rebuild`, so NuGet restoration is part of the backend build.
- `verifyPlugin` obtains Plugin Verifier through the IntelliJ Platform Gradle Plugin. The IDE being checked remains the configured local Rider installation.

The RD model JAR is the separate input you obtain with the single-entry extraction command below. The downloader and RD artifact provider both verify its locked size and SHA-256. A copy of the maintainer's caches is not required. Initial restoration needs network access, usable NuGet package sources, and any proxy settings required by your network. The build isolates cache locations; it does not replace the machine's or user's NuGet configuration.

## Configure the Rider installation

Run the commands in this document from the source root. Create the ignored local configuration:

```powershell
Copy-Item .\local.properties.example .\local.properties
```

Edit `local.properties` to point to the installed Rider directory, using an absolute path:

```properties
RiderHome=D:/Path/To/JetBrains Rider
```

`build.ps1` passes this value to Gradle, the Java toolchain, and MSBuild. Use that entry point for every Gradle build, generation, verification, and sandbox task.

The script sets `JAVA_HOME`, `GRADLE_USER_HOME`, `DOTNET_CLI_HOME`, and the NuGet cache paths for its process. By default, build outputs, extracted SDK input, Gradle/NuGet caches, and the Rider sandbox stay under the source root. These local working files should not be committed or included in source archives. No global Gradle or JDK installation is required. The build script does not change the system PATH or persistent environment variables.

The optional `-CacheRoot` parameter reuses the `.gradle-user-home` and `.nuget` directories under an existing absolute directory. Omit it to use the source root. For example, point this command at a directory containing caches you already use:

```powershell
pwsh -NoProfile -File .\build.ps1 buildPlugin -CacheRoot "D:\BuildCaches\ReSharperMcpToolset"
```

This shares downloaded dependencies without copying large cache directories. `.dotnet-home`, project caches, generated files, compiler outputs, and the Rider sandbox remain local to the source being built. The script fixes the working directory and Gradle project directory to its own source root, even when invoked from another directory.

## Obtain the RD model input

The verified local Rider installation does not include the `rider-model.jar` required by RD generation. The supplied helper reads the matching JetBrains distribution using HTTP Range requests and extracts the requested entry; it does not download the complete Rider archive.

The tracked `rider-model.lock.json` records the Rider build, official distribution URI, ZIP entry, byte count, and SHA-256. Use those values together:

```powershell
$riderModelLock = Get-Content -LiteralPath .\rider-model.lock.json -Raw | ConvertFrom-Json
pwsh -NoProfile -File .\tools\Get-RemoteZipEntry.ps1 `
  -Uri $riderModelLock.uri `
  -EntryName $riderModelLock.entryName `
  -ExpectedSize $riderModelLock.size `
  -ExpectedSha256 $riderModelLock.sha256 `
  -OutputPath ".sdk/rider-model.jar" `
  -CentralDirectoryCachePath ".sdk/riderRD-2026.2.2-central-verified.bin"
```

Expected size and SHA-256 are required downloader arguments. A successful extraction verifies them before replacing the output file; printing a newly computed hash alone is not accepted as verification. `RangeTimeoutSeconds` covers each request's headers and body (default 60 seconds). Transient range failures retry up to `MaxAttempts` (default 5); invalid range responses or changed object identities fail without retrying their bodies.

The central-directory cache has a `.metadata.json` sidecar binding its URI, strong ETag, archive length, byte range and committed prefix digest. Existing unbound, mismatched or corrupted caches are rejected and preserved; select a new cache path instead of deleting unrelated cached dependencies. Only complete verified chunks are committed. Cache mode requires a strong ETag; without one, omit the cache argument for a fresh extraction that still must pass the expected size/hash checks. Relative paths use the current filesystem directory, full absolute paths are accepted, and drive-relative/root-relative or conflicting destination paths are rejected before network access.

Check an existing model without generating or compiling the plugin:

```powershell
pwsh -NoProfile -File .\build.ps1 verifyRiderModel --offline
```

The same size/hash validation runs whenever the model artifact is selected for the RD classpath. A missing, wrong-size, or same-size corrupted file is rejected. The lock's Rider build must match `RiderBuild`. If a check fails, investigate the mismatch instead of accepting a new hash. Keep the JAR in the ignored `.sdk` directory; do not include it in source archives or plugin packages.

For an isolated integrity fixture, `--project-prop 'RiderModelFile=build/model-fixture.jar'` selects a different file to read. Relative paths are resolved from the source root; absolute paths are accepted. Use this two-argument Gradle option for Windows absolute paths so PowerShell does not split a drive colon inside a combined `-P` argument. The override cannot change the expected size/hash or write to the selected file, and applies to both `verifyRiderModel` and RD artifact selection.

## Generate, compile, and package

Run the existing verification sequence:

```powershell
pwsh -NoProfile -File .\build.ps1 :protocol:rdgen compileDotNet compileKotlin
pwsh -NoProfile -File .\build.ps1 buildPlugin
pwsh -NoProfile -File .\build.ps1 verifyPluginProjectConfiguration
pwsh -NoProfile -File .\build.ps1 verifyPlugin
```

When validating an exported source snapshot, append `--rerun-tasks --no-build-cache` to each command above. This forces task execution and prevents reuse of cached task outputs, while still allowing downloaded dependencies to be reused, including through `-CacheRoot`. It checks the exported source inputs without requiring a full download into empty caches.

The protocol source is `protocol/src/main/kotlin/model/rider/BathurReSharperMcpToolsetModel.kt`. RD generation produces the Kotlin and C# `BathurReSharperMcpToolsetModel.Generated` files and adds their project license notices as part of the generation task. Keep the generated files in the source set, regenerate them from the model, and review any differences; do not edit generated code by hand.

`BuildConfiguration` in `gradle.properties` selects the backend build configuration and defaults to `Release`. Release settings disable backend PDB and CodeView debug-symbol output, enable deterministic compilation, map source paths, and disable automatic source-control/SourceLink metadata. Debug builds retain their normal debugging configuration.

`buildPlugin` writes a ZIP under `build/distributions`, containing the plugin's Kotlin/JVM JAR and backend DLL without a backend PDB. Rider's referenced backend assemblies are marked `Private=false` and are not copied into the plugin package. `dependencies.json` is host metadata, not a complete inventory of build dependencies.

The plugin ZIP includes `LICENSE`, `LICENSING.md`, `THIRD_PARTY_NOTICES.md`, the required third-party license files, and generated `SOURCE_INFO.txt`. The latter identifies the plugin version and its matching `ReSharperMcpToolset-<version>-source.zip`, obtained with `SHA256SUMS` from the same distribution location as the plugin ZIP.

Plugin Verifier is configured to check only the installed, locked Rider build. Its default recommended IDE downloads are disabled. The existing configuration suppresses only `TemplateWordInPluginId` and `TemplateWordInPluginName`; those suppressions do not establish compatibility with other IDE builds. Static verification also does not exercise C++ semantic behavior in a loaded project.

Configuration validation can recommend removing `until-build`; this project deliberately retains the upper bound to match its exact Rider build restriction.

The downloader has a separate offline regression task:

```powershell
pwsh -NoProfile -File .\build.ps1 remoteZipEntryTest --offline
# Select an existing Python 3 runtime when python/python3 is not on PATH:
pwsh -NoProfile -File .\build.ps1 remoteZipEntryTest --offline --project-prop 'RemoteZipTestPython=C:/Path/To/python.exe'
```

This task does not depend on plugin compilation. Its PowerShell harness starts a Python standard-library HTTP fixture bound to loopback, uses small ZIP inputs, and records evidence below `build/remote-zip-tests`. It does not contact the Rider download service or modify the existing `.sdk` model and cache. Python is required only for this regression task, not for normal plugin compilation.

The detached logging and call-observation checks run without launching Rider:

```powershell
pwsh -NoProfile -File .\build.ps1 failureLogTest
```

The harness uses the locked IDE's bundled libraries and writes temporary evidence under `build/failure-log-tests`. It covers bounded records, queue saturation, writer failures, file rotation/retention, active-file reading and cleanup from separate JVM processes, and preservation of tool results and exceptions. It does not exercise a loaded C++ project or the Advanced Settings UI. With dependencies already cached, `--offline '-Pkotlin.compiler.execution.strategy=in-process'` can be appended to keep Kotlin compilation in the Gradle process.

Consumer-diagnostic regression checks also run without launching Rider:

```powershell
pwsh -NoProfile -File .\build.ps1 consumerDiagnosticsTest
```

This task rebuilds the backend and checks concise omission messages, symbol-kind and hierarchy-result classification, relationship uncertainty, qualifier-group child traversal, and direct virtual-base selection against isolated objects from the locked SDK. The virtual-base cases use precomputed in-memory relationships; they do not test C++ parsing or inheritance construction. It uses no additional test framework and does not replace validation in a loaded C++ project.

Timeout and registration-safety checks also run without launching Rider:

```powershell
pwsh -NoProfile -File .\build.ps1 ownTimeoutTest registrationSafetyTest
```

These JVM harnesses call the production helpers. They cover timeout ownership and cancellation, real-path containment, registration failure stages, and retry guidance. Registration fixtures stay under `build/registration-safety-tests`; directory junctions created on Windows are removed individually after each case. These checks do not invoke Rider's Add Existing Item action or simulate a live RD disconnection.

Exact-name search checks use the same backend build and locked SDK:

```powershell
pwsh -NoProfile -File .\build.ps1 searchNamesTest
```

They cover the ordinary-name fast path, SDK name values and exact matching for templates, conversion and literal operators, bounded index keys, and syntax/infrastructure failure classification. The isolated harness does not parse special-name fragments in a live C++ module or query Rider's symbol index; those behaviors require runtime validation.

When comparing a local build with a published package, use the plugin version, source revision, and checksums supplied with the release you are using. A different source revision, build configuration, or archive content can produce a different package; do not assume a local rebuild is byte-for-byte identical.

## Optional runtime validation

Runtime checks require Rider to load and index a suitable C++ project and expose its MCP server. The plugin is intended for use with Rider's existing project model; building the plugin does not require building Unreal Engine or a game project.

An isolated Rider sandbox can be started through the same entry point:

```powershell
pwsh -NoProfile -File .\build.ps1 runIde
```

Coordinate this GUI step with the person using the workstation. Avoid running a daily Rider instance and a sandbox against the same Unreal solution and caches at the same time. Install or enable the plugin, verify its loaded version, and reconnect the MCP client after Rider is ready. Recorded runtime observations and their limits are described in [VALIDATION.md](VALIDATION.md).
