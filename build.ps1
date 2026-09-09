# Copyright (C) 2026 Bathur.
# Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
# See LICENSE and LICENSING.md in the public source root.

[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]] $GradleArguments = @("help"),

    # Parent of shared Gradle/NuGet caches; defaults to this source root.
    # Project state, build outputs and .dotnet-home remain source-local.
    [string] $CacheRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$cacheBase = $repoRoot
if (-not [string]::IsNullOrWhiteSpace($CacheRoot)) {
    if (-not [System.IO.Path]::IsPathFullyQualified($CacheRoot)) {
        throw "CacheRoot must be an absolute directory path."
    }
    $cacheBase = [System.IO.Path]::GetFullPath($CacheRoot)
    if (-not (Test-Path -LiteralPath $cacheBase -PathType Container)) {
        throw "CacheRoot does not name an existing directory: '$cacheBase'."
    }
}

$localPropertiesPath = Join-Path $repoRoot "local.properties"

if (-not (Test-Path -LiteralPath $localPropertiesPath)) {
    throw "Local configuration was not found at '$localPropertiesPath'. Copy local.properties.example to local.properties and set RiderHome."
}

$riderHomeProperty = Get-Content -LiteralPath $localPropertiesPath |
    Where-Object { $_ -match '^\s*RiderHome\s*=' } |
    Select-Object -Last 1

if ($null -eq $riderHomeProperty) {
    throw "RiderHome is not configured in '$localPropertiesPath'."
}

$riderHomeValue = ($riderHomeProperty -split '=', 2)[1].Trim()
if ([string]::IsNullOrWhiteSpace($riderHomeValue)) {
    throw "RiderHome is empty in '$localPropertiesPath'."
}

if (-not [System.IO.Path]::IsPathFullyQualified($riderHomeValue)) {
    throw "RiderHome must be an absolute directory path."
}

$riderHome = [System.IO.Path]::GetFullPath($riderHomeValue)
$javaHome = Join-Path $riderHome "jbr"
$javaExecutable = Join-Path $javaHome "bin\java.exe"
$wrapperJar = Join-Path $repoRoot "gradle\wrapper\gradle-wrapper.jar"

if (-not (Test-Path -LiteralPath $javaExecutable)) {
    throw "Rider JBR was not found at '$javaExecutable'."
}

if (-not (Test-Path -LiteralPath $wrapperJar)) {
    throw "Gradle Wrapper JAR was not found at '$wrapperJar'."
}

$env:JAVA_HOME = $javaHome
$env:GRADLE_USER_HOME = Join-Path $cacheBase ".gradle-user-home"
$env:DOTNET_CLI_HOME = Join-Path $repoRoot ".dotnet-home"
$env:NUGET_PACKAGES = Join-Path $cacheBase ".nuget\packages"
$env:NUGET_HTTP_CACHE_PATH = Join-Path $cacheBase ".nuget\http-cache"
$env:NUGET_PLUGINS_CACHE_PATH = Join-Path $cacheBase ".nuget\plugins-cache"
$env:NUGET_SCRATCH = Join-Path $cacheBase ".nuget\scratch"
$env:DOTNET_CLI_TELEMETRY_OPTOUT = "1"
$env:DOTNET_SKIP_FIRST_TIME_EXPERIENCE = "1"
$env:DOTNET_GENERATE_ASPNET_CERTIFICATE = "0"

Push-Location -LiteralPath $repoRoot
try {
    & $javaExecutable `
        "-Dorg.gradle.appname=gradlew" `
        "-classpath" $wrapperJar `
        "org.gradle.wrapper.GradleWrapperMain" `
        "--no-daemon" `
        "--project-dir" $repoRoot `
        "--project-cache-dir" (Join-Path $repoRoot ".gradle") `
        "-PRiderHome=$riderHome" `
        "-Porg.gradle.java.installations.paths=$javaHome" `
        @GradleArguments
    $buildExitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}

if ($buildExitCode -ne 0) {
    exit $buildExitCode
}
