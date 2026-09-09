// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    java
    alias(libs.plugins.kotlinJvm)
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

val DotnetPluginId: String by project
val DotnetAssemblyName: String by project
val DotnetSolution: String by project
val BuildConfiguration: String by project
val RiderHome: String by project
val RiderBuild: String by project

version = providers.gradleProperty("PluginVersion").get()

val pluginDescriptor = DocumentBuilderFactory.newInstance().apply {
    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
}.newDocumentBuilder().parse(file("src/rider/main/resources/META-INF/plugin.xml"))
check(pluginDescriptor.getElementsByTagName("version").item(0)?.textContent == version.toString()) {
    "plugin.xml version must match PluginVersion."
}
val descriptorCompatibility = pluginDescriptor.getElementsByTagName("idea-version").item(0)?.attributes
check(
    descriptorCompatibility?.getNamedItem("since-build")?.nodeValue == RiderBuild &&
        descriptorCompatibility?.getNamedItem("until-build")?.nodeValue == RiderBuild
) {
    "plugin.xml must restrict both since-build and until-build to $RiderBuild."
}

val riderBuildFile = file("$RiderHome/build.txt")
check(riderBuildFile.isFile) {
    "Rider build marker does not exist: $riderBuildFile"
}

val actualRiderBuild = riderBuildFile.readText().trim()
check(actualRiderBuild == "RD-$RiderBuild") {
    "Expected Rider build RD-$RiderBuild, but found $actualRiderBuild at $RiderHome."
}

allprojects {
    repositories {
        mavenCentral()
    }
}

repositories {
    intellijPlatform {
        defaultRepositories()
    }
}

sourceSets {
    main {
        java.srcDir("src/rider/main/java")
        kotlin.srcDir("src/rider/main/kotlin")
        resources.srcDir("src/rider/main/resources")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_25.toString()
    targetCompatibility = JavaVersion.VERSION_25.toString()
    options.release.set(25)
}

dependencies {
    intellijPlatform {
        local(RiderHome)
        bundledPlugin("com.intellij.mcpServer")
        bundledModule("intellij.rider.cpp.core.languages")
        bundledModule("intellij.rider.rdclient.dotnet")
    }
}

tasks.compileKotlin {
    dependsOn(":protocol:rdgen")
}

val compileDotNet by tasks.registering(Exec::class) {
    dependsOn(":protocol:rdgen")
    workingDir(rootDir)
    commandLine(
        "dotnet",
        "msbuild",
        DotnetSolution,
        "/t:Restore;Rebuild",
        "/p:Configuration=$BuildConfiguration",
        "/p:RiderHome=$RiderHome",
        "/p:PluginVersion=${project.version}",
        "/p:HostFullIdentifier=",
        "/v:minimal"
    )
}

val publicationRoot = if (file("LICENSE").isFile) rootDir else file("release")
val publicationDocuments = listOf("LICENSE", "LICENSING.md", "THIRD_PARTY_NOTICES.md")
val publicationLicenseFiles = listOf("Apache-2.0.txt", "Gradle-Wrapper-NOTICE.txt")
val validatePublicationMaterials by tasks.registering {
    val documents = publicationDocuments.map { publicationRoot.resolve(it) }
    val licenses = publicationLicenseFiles.map { publicationRoot.resolve("licenses/$it") }
    inputs.files(documents + licenses)

    doLast {
        (documents + licenses).forEach { material ->
            check(material.isFile && material.length() > 0) {
                "Required publication material is missing or empty: $material"
            }
        }
    }
}

val sourceInfoFile = layout.buildDirectory.file("publication/SOURCE_INFO.txt")
val generateSourceInfo by tasks.registering {
    inputs.property("pluginVersion", project.version.toString())
    outputs.file(sourceInfoFile)

    doLast {
        val output = sourceInfoFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            "ReSharper MCP Toolset ${project.version}\n" +
                "Corresponding Source: ReSharperMcpToolset-${project.version}-source.zip\n" +
                "Obtain this source archive and SHA256SUMS from the same distribution location as this plugin ZIP.\n" +
                "Use the matching source version and verify both archives against SHA256SUMS.\n",
            Charsets.UTF_8
        )
    }
}

tasks.processResources {
    from("dependencies.json") {
        into("META-INF")
    }
}

tasks.prepareSandbox {
    dependsOn(compileDotNet, validatePublicationMaterials, generateSourceInfo)

    from(publicationRoot) {
        include(publicationDocuments)
        into(rootProject.name)
    }
    from(publicationRoot.resolve("licenses")) {
        include(publicationLicenseFiles)
        into("${rootProject.name}/licenses")
    }
    from(sourceInfoFile) {
        into(rootProject.name)
    }

    val outputFolder = layout.projectDirectory.dir(
        "src/dotnet/$DotnetPluginId/bin/$DotnetPluginId.Rider/$BuildConfiguration"
    )
    val backendFiles = listOf(
        outputFolder.file("$DotnetAssemblyName.dll")
    )

    backendFiles.forEach { backendFile ->
        from(backendFile) {
            into("${rootProject.name}/dotnet")
        }
    }

    doLast {
        backendFiles.forEach { backendFile ->
            check(backendFile.asFile.exists()) {
                "Backend output does not exist: ${backendFile.asFile}"
            }
        }
    }
}

tasks.runIde {
    maxHeapSize = "4096m"
}

intellijPlatform {
    buildSearchableOptions = false
    sandboxContainer = layout.projectDirectory.dir("rider-sandbox")

    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = RiderBuild
            untilBuild = RiderBuild
        }
    }

    pluginVerification {
        // The plugin name identifies its ReSharper integration.
        // Keep every other structure and compatibility check enabled.
        freeArgs = listOf(
            "-mute",
            "TemplateWordInPluginId,TemplateWordInPluginName"
        )

        ides {
            local(RiderHome)
        }
    }
}

val riderModel: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(riderModel.name, provider {
        layout.projectDirectory.file(".sdk/rider-model.jar").asFile.also {
            check(it.isFile) {
                "rider-model.jar was not extracted into .sdk"
            }
        }
    })
}
