// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

import com.jetbrains.rd.generator.gradle.RdGenTask

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.jetbrains.rdgen") version libs.versions.rdGen
}

dependencies {
    implementation(libs.kotlinStdLib)
    implementation(libs.rdGen)
    implementation(
        project(
            mapOf(
                "path" to ":",
                "configuration" to "riderModel"
            )
        )
    )
}

val DotnetPluginId: String by rootProject
val RiderPluginId: String by rootProject

val generatedLicenseHeader = """
    // Copyright (C) 2026 Bathur.
    // Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
    // See LICENSE and LICENSING.md in the public source root.
""".trimIndent() + "\n\n"
val generatedModelFiles = listOf(
    File(rootDir, "src/dotnet/$DotnetPluginId/BathurReSharperMcpToolsetModel.Generated.cs"),
    File(
        rootDir,
        "src/rider/main/kotlin/${RiderPluginId.replace('.', '/')}/BathurReSharperMcpToolsetModel.Generated.kt"
    )
)

rdgen {
    val csharpOutput = File(rootDir, "src/dotnet/$DotnetPluginId")
    val kotlinOutput = File(
        rootDir,
        "src/rider/main/kotlin/${RiderPluginId.replace('.', '/')}"
    )

    verbose = true
    packages = "model.rider"

    generator {
        language = "kotlin"
        transform = "asis"
        root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
        namespace = "local.bathur.resharper.mcp.toolset"
        directory = kotlinOutput.path
    }

    generator {
        language = "csharp"
        transform = "reversed"
        root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
        namespace = "Bathur.ReSharperMcpToolset.Protocol"
        directory = csharpOutput.path
    }
}

tasks.withType<RdGenTask> {
    val classPath = sourceSets["main"].runtimeClasspath
    dependsOn(classPath)
    classpath(classPath)
    inputs.property("generatedLicenseHeader", generatedLicenseHeader)
    outputs.files(generatedModelFiles)

    doLast {
        generatedModelFiles.forEach { generatedFile ->
            check(generatedFile.isFile) { "RD generated file is missing: $generatedFile" }
            val generatedText = generatedFile.readText(Charsets.UTF_8)
            if (!generatedText.replace("\r\n", "\n").removePrefix("\uFEFF").startsWith(generatedLicenseHeader)) {
                generatedFile.writeText(generatedLicenseHeader + generatedText, Charsets.UTF_8)
            }
        }
    }
}
