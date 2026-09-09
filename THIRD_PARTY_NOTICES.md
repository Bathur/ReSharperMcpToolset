# Third-party notices and acknowledgments

This document describes the project's host dependencies, build inputs, and related work. Third-party files retain their original licenses and notices.

## Gradle Wrapper in the source distribution

The source distribution includes the unmodified `gradle/wrapper/gradle-wrapper.jar` from **Gradle 8.13**, under the **Apache License, Version 2.0**. Its SHA-256 is `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`, matching Gradle's [official Wrapper checksum](https://gradle.org/release-checksums/). The Wrapper bootstrap is a separate input from the **Gradle 9.1.0** distribution selected by `gradle-wrapper.properties`.

[licenses/Apache-2.0.txt](licenses/Apache-2.0.txt) preserves the JAR's `META-INF/LICENSE` verbatim. The JAR contains no separate `NOTICE` file; [licenses/Gradle-Wrapper-NOTICE.txt](licenses/Gradle-Wrapper-NOTICE.txt) is this project's attribution and provenance record, not an upstream Gradle notice. `build.ps1` launches the Wrapper directly; this source set does not include `gradlew` or `gradlew.bat` scripts.

The Wrapper JAR is source-build tooling, not part of the plugin installation. The full Gradle distribution and its bundled third-party libraries are downloaded build inputs, not included in this project's source archive or plugin ZIP. Their own bundled licenses and notices remain with those downloaded components.

## JetBrains Rider and ReSharper C++

The plugin integrates with JetBrains Rider's built-in MCP server and uses ReSharper C++ semantic services inside Rider. The Kotlin frontend depends on the Rider platform, C++ support, and MCP extension APIs. The C# backend links to assemblies from the installed Rider ReSharper host.

Rider and ReSharper components are obtained from the user's Rider installation and remain under their own terms. The backend project marks those assembly references as non-copying references. The plugin distribution excludes a Rider installation, ReSharper host assemblies, and the extracted `rider-model.jar` used for protocol generation.

The additional permission in [LICENSING.md](LICENSING.md) concerns the plugin's own license. It grants no rights to JetBrains components.

## Build tools and dependencies

The build uses Gradle and its Wrapper, Kotlin, the IntelliJ Platform Gradle Plugin, the JetBrains RD generator, the .NET SDK, and the target Rider's Java and ReSharper runtimes. The build instructions record the selected versions and describe which inputs are obtained locally or downloaded.

| Component | Role | Upstream |
| --- | --- | --- |
| Gradle 9.1.0 | Downloaded build execution distribution | [Gradle](https://github.com/gradle/gradle/tree/v9.1.0) |
| Gradle Wrapper 8.13 | Bootstrap JAR included in source inputs; Apache-2.0 as described above | [Gradle Wrapper source](https://github.com/gradle/gradle/tree/v8.13.0) |
| Kotlin | Kotlin compiler and related libraries | [Kotlin](https://github.com/JetBrains/kotlin) |
| IntelliJ Platform Gradle Plugin | Rider plugin assembly and verification tasks | [IntelliJ Platform Gradle Plugin](https://github.com/JetBrains/intellij-platform-gradle-plugin) |
| JetBrains RD | Protocol generation and host communication | [RD](https://github.com/JetBrains/rd) |
| .NET SDK | Builds the C# backend | [.NET SDK](https://github.com/dotnet/sdk) |

This table identifies build and host roles. It does not list the contents of a plugin archive or every resolved transitive dependency. The project's `dependencies.json` is host metadata, not a software bill of materials.

Redistributed third-party files remain subject to their upstream licenses. Applicable original notices and license texts must accompany those files. No third-party file is relicensed merely by being included alongside this project.

## Related work

[joshua-light/resharper-mcp](https://github.com/joshua-light/resharper-mcp), by Joshua Light, provided useful investigation leads for ReSharper's headless daemon APIs. The diagnostics investigation consulted commit `78b5a02` and recorded that its implementation code was not copied in that work. The related project is published under the MIT License.

ReSharper MCP Toolset uses Rider's built-in MCP extension point and a Kotlin/RD/C# path to ReSharper C++. Related work is acknowledged for the ideas and API leads it provided; its supported languages, architecture, and validation claims do not establish this project's compatibility.

## Unreal Engine and Lyra

Unreal Engine and Lyra provided the practical UE C++ integration scenarios used during development. They are separately obtained test environments. Their source code, assets, generated project files, and build outputs are excluded from this project's source and plugin archives.

## Names

JetBrains, Rider, and ReSharper are names associated with JetBrains; Unreal Engine and Lyra are associated with Epic Games. Other product names belong to their respective owners. Mentioning them identifies integration targets, build inputs, and testing context; this project does not claim affiliation or endorsement.
