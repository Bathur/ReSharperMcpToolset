# Copyright (C) 2026 Bathur.
# Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
# See LICENSE and LICENSING.md in the public source root.

param(
    [string] $PythonPath,
    [string] $EvidenceRoot = (Join-Path $PSScriptRoot '../build/remote-zip-tests')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($PythonPath)) {
    foreach ($commandName in @('python', 'python3')) {
        $command = Get-Command $commandName -CommandType Application -ErrorAction SilentlyContinue
        if ($null -ne $command -and $command.Source -notlike '*\Microsoft\WindowsApps\*') {
            $PythonPath = $command.Source
            break
        }
    }
}
if ([string]::IsNullOrWhiteSpace($PythonPath)) {
    throw 'Python 3 is required for the loopback fixture. Pass -PythonPath; this test installs nothing.'
}

$testRoot = Join-Path ([IO.Path]::GetFullPath($EvidenceRoot)) ([guid]::NewGuid().ToString())
$null = [IO.Directory]::CreateDirectory($testRoot)
$downloader = Join-Path $PSScriptRoot 'Get-RemoteZipEntry.ps1'
$fixtureScript = Join-Path $PSScriptRoot 'remote_zip_fixture.py'
$readyFile = Join-Path $testRoot 'server-ready.json'
$serverOut = Join-Path $testRoot 'server.stdout.log'
$serverErr = Join-Path $testRoot 'server.stderr.log'
$pwshPath = (Get-Process -Id $PID).Path
$results = [Collections.Generic.List[object]]::new()
$sentinel = [Text.Encoding]::UTF8.GetBytes('Existing valid output must survive a rejected download.')
$sentinelHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($sentinel))
$server = $null
$baseUrl = $null
$metadata = $null

function Configure-Fixture {
    param([string] $Id, [string] $Mode = 'valid', [string] $Archive = 'stored', [int] $Revision = 1)
    $body = @{ id = $Id; mode = $Mode; archive = $Archive; revision = $Revision } | ConvertTo-Json -Compress
    $null = Invoke-RestMethod -Uri "$baseUrl/control" -Method Post -ContentType 'application/json' -Body $body -NoProxy
}

function Get-FixtureStats {
    param([string] $Id)
    $deadline = [DateTime]::UtcNow.AddSeconds(6)
    do {
        $stats = Invoke-RestMethod -Uri "$baseUrl/stats/$Id" -NoProxy
        if (@($stats.requests | Where-Object { -not $_.finished }).Count -eq 0) { return $stats }
        Start-Sleep -Milliseconds 50
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Fixture request did not finish: $Id"
}

function Invoke-DownloadCase {
    param(
        [string] $Name,
        [string] $Id = $Name,
        [string] $Archive = 'stored',
        [ValidateSet('success', 'failure')][string] $Expect = 'success',
        [string] $OutputPath = 'output.jar',
        [string] $CachePath,
        [long] $ExpectedSize = -1,
        [string] $ExpectedHash,
        [int] $Attempts = 2,
        [int] $RangeTimeout = 3,
        [switch] $InvalidOutputPath,
        [switch] $NoHttp,
        [switch] $PermanentRejection,
        [switch] $BoundedBody,
        [switch] $ExpectRetry,
        [switch] $StalledBody,
        [switch] $PreserveCache
    )
    $directory = Join-Path $testRoot $Name
    $null = [IO.Directory]::CreateDirectory($directory)
    $fixture = $metadata.PSObject.Properties[$Archive].Value
    if ($ExpectedSize -lt 0) { $ExpectedSize = $fixture.entry_size }
    if ([string]::IsNullOrEmpty($ExpectedHash)) { $ExpectedHash = $fixture.sha256 }
    $resolvedOutput = if ($InvalidOutputPath) {
        Join-Path $directory 'output.jar'
    } else {
        [IO.Path]::GetFullPath($OutputPath, $directory)
    }
    $null = [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($resolvedOutput))
    [IO.File]::WriteAllBytes($resolvedOutput, $sentinel)
    $cacheBefore = @()
    if ($PreserveCache) {
        $resolvedCache = [IO.Path]::GetFullPath($CachePath, $directory)
        $cacheBefore = @($resolvedCache, "$resolvedCache.metadata.json") | Where-Object { [IO.File]::Exists($_) } |
            ForEach-Object { [pscustomobject]@{ path = $_; hash = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash } }
    }

    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $pwshPath
    $start.WorkingDirectory = $directory
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in @(
        '-NoProfile', '-NonInteractive', '-File', $downloader,
        '-Uri', "$baseUrl/artifact/$Id", '-EntryName', 'lib/rd/a.jar',
        '-OutputPath', $OutputPath, '-ExpectedSize', $ExpectedSize.ToString(),
        '-ExpectedSha256', $ExpectedHash, '-ChunkSize', '65536',
        '-MaxAttempts', $Attempts.ToString(), '-RangeTimeoutSeconds', $RangeTimeout.ToString()
    )) { $start.ArgumentList.Add($argument) }
    if (-not [string]::IsNullOrEmpty($CachePath)) {
        $start.ArgumentList.Add('-CentralDirectoryCachePath')
        $start.ArgumentList.Add($CachePath)
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    $watch = [Diagnostics.Stopwatch]::StartNew()
    try {
        $null = $process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(30000)) {
            $process.Kill($true)
            $process.WaitForExit()
            throw "Downloader process exceeded the 30-second test limit: $Name"
        }
        $watch.Stop()
        $exitCode = $process.ExitCode
        [IO.File]::WriteAllText((Join-Path $directory 'stdout.log'), $stdout.GetAwaiter().GetResult())
        [IO.File]::WriteAllText((Join-Path $directory 'stderr.log'), $stderr.GetAwaiter().GetResult())
    } finally {
        $process.Dispose()
    }

    $stats = Get-FixtureStats $Id
    $requests = @($stats.requests)
    $gets = @($requests | Where-Object method -eq 'GET')
    $hash = (Get-FileHash -LiteralPath $resolvedOutput -Algorithm SHA256).Hash
    if ($exitCode -eq 0) {
        if ($Expect -eq 'failure' -or $hash -cne $fixture.sha256) {
            throw "Case '$Name' succeeded with an unexpected output. Evidence: $directory"
        }
    } elseif ($Expect -eq 'success' -or $hash -cne $sentinelHash) {
        throw "Case '$Name' failed unexpectedly or damaged the previous output. Evidence: $directory"
    }
    foreach ($cached in $cacheBefore) {
        if (-not [IO.File]::Exists($cached.path) -or (Get-FileHash -LiteralPath $cached.path -Algorithm SHA256).Hash -cne $cached.hash) {
            throw "Case '$Name' modified a rejected cache or its metadata."
        }
    }
    if ($NoHttp -and $requests.Count -ne 0) { throw "Case '$Name' made HTTP requests before rejecting its path." }
    if ($PermanentRejection -and $gets.Count -ne 1) { throw "Permanent protocol error retried in '$Name'." }
    if ($BoundedBody) {
        foreach ($get in $gets) {
            # Bytes written by the server bound bytes the client could consume. Allow a
            # small socket-buffer race, never the fixture's complete 2 MiB response.
            $limit = if ($stats.mode -eq 'oversized_stream') { $get.expected + 65536 } else { 65536 }
            if ($get.sent -gt $limit) { throw "Case '$Name' consumed an unbounded response ($($get.sent) bytes)." }
        }
    }
    if ($ExpectRetry -and (@($gets | Where-Object status -eq 503).Count -ne 1 -or $gets.Count -lt 2)) {
        throw "Case '$Name' did not recover from the single transient HTTP error."
    }
    if ($StalledBody -and $watch.ElapsedMilliseconds -gt 5000) {
        throw "Case '$Name' exceeded the bounded body-read timeout."
    }
    $result = [pscustomobject]@{
        name = $Name; exit_code = $exitCode; elapsed_ms = $watch.ElapsedMilliseconds
        output_sha256 = $hash; requests = $requests
    }
    $results.Add($result)
    Write-Host "PASS: $Name ($($watch.ElapsedMilliseconds) ms)"
    return $result
}

try {
    # Quoted paths are passed to a hidden helper; no shell command is assembled from responses.
    $serverArguments = @('-B', '-u', ('"' + $fixtureScript + '"'), '--ready-file', ('"' + $readyFile + '"'))
    $launch = @{
        FilePath = $PythonPath; ArgumentList = $serverArguments; PassThru = $true
        RedirectStandardOutput = $serverOut; RedirectStandardError = $serverErr
    }
    if ($IsWindows) { $launch.WindowStyle = 'Hidden' }
    $server = Start-Process @launch
    $readyDeadline = [DateTime]::UtcNow.AddSeconds(10)
    while (-not [IO.File]::Exists($readyFile)) {
        if ($server.HasExited) { throw "Loopback fixture exited during startup. See $serverErr" }
        if ([DateTime]::UtcNow -ge $readyDeadline) { throw "Loopback fixture did not become ready. See $serverErr" }
        Start-Sleep -Milliseconds 50
    }
    $baseUrl = ([IO.File]::ReadAllText($readyFile) | ConvertFrom-Json).base_url
    if ($baseUrl -notmatch '^http://127\.0\.0\.1:[0-9]+$') { throw 'The fixture must bind only to IPv4 loopback.' }
    $metadata = Invoke-RestMethod -Uri "$baseUrl/meta" -NoProxy

    Configure-Fixture 'stored-relative'
    $null = Invoke-DownloadCase 'stored-relative' -OutputPath 'relative folder/entry.jar' -CachePath 'relative folder/central.bin'
    Configure-Fixture 'stored-absolute'
    $absoluteDirectory = Join-Path $testRoot 'absolute paths'
    $null = Invoke-DownloadCase 'stored-absolute' -OutputPath (Join-Path $absoluteDirectory 'entry.jar') -CachePath (Join-Path $absoluteDirectory 'central.bin')
    foreach ($archive in @('deflated', 'case_collision')) {
        Configure-Fixture $archive -Archive $archive
        $null = Invoke-DownloadCase $archive -Archive $archive
    }
    Configure-Fixture 'no-length' -Mode 'no_length'
    $null = Invoke-DownloadCase 'no-length'
    Configure-Fixture 'transient' -Mode 'transient'
    $null = Invoke-DownloadCase 'transient' -ExpectRetry
    Configure-Fixture 'validator-during-range' -Mode 'changed_validator'
    $null = Invoke-DownloadCase 'validator-during-range' -Expect failure -Attempts 3 -PermanentRejection
    Configure-Fixture 'etag-pinned-by-get' -Mode 'head_no_etag'
    $pinned = Invoke-DownloadCase 'etag-pinned-by-get' -CachePath 'central.bin'
    $pinnedGets = @($pinned.requests | Where-Object method -eq 'GET')
    if ($null -ne $pinnedGets[0].if_match -or @($pinnedGets | Select-Object -Skip 1 | Where-Object { [string]::IsNullOrEmpty($_.if_match) }).Count -ne 0) {
        throw 'The first validated Range response must pin subsequent If-Match requests when HEAD has no ETag.'
    }
    Configure-Fixture 'no-etag-fresh' -Mode 'no_etag'
    $null = Invoke-DownloadCase 'no-etag-fresh'
    Configure-Fixture 'no-etag-cache' -Mode 'no_etag'
    $null = Invoke-DownloadCase 'no-etag-cache' -CachePath 'central.bin' -Expect failure

    foreach ($mode in @('ignored_range', 'bad_range', 'bad_total', 'encoded', 'oversized_length', 'oversized_stream')) {
        Configure-Fixture $mode -Mode $mode
        $null = Invoke-DownloadCase $mode -Expect failure -Attempts 3 -PermanentRejection -BoundedBody
    }
    Configure-Fixture 'truncated' -Mode 'truncated'
    $null = Invoke-DownloadCase 'truncated' -Expect failure -Attempts 1
    Configure-Fixture 'stalled' -Mode 'stalled'
    $null = Invoke-DownloadCase 'stalled' -Expect failure -Attempts 1 -RangeTimeout 1 -StalledBody
    Configure-Fixture 'bad-sha'
    $null = Invoke-DownloadCase 'bad-sha' -Expect failure -ExpectedHash ('0' * 64)
    Configure-Fixture 'bad-size'
    $null = Invoke-DownloadCase 'bad-size' -Expect failure -ExpectedSize ($metadata.stored.entry_size + 1)
    Configure-Fixture 'damaged' -Archive 'damaged'
    $null = Invoke-DownloadCase 'damaged' -Archive 'damaged' -Expect failure

    $lockedCache = Join-Path $testRoot 'preexisting-lock-central.bin'
    $existingLock = "$lockedCache.lock"
    [IO.File]::WriteAllBytes($existingLock, $sentinel)
    Configure-Fixture 'preexisting-lock'
    $null = Invoke-DownloadCase 'preexisting-lock' -CachePath $lockedCache -Expect failure
    if (-not [IO.File]::Exists($existingLock) -or
        (Get-FileHash -LiteralPath $existingLock -Algorithm SHA256).Hash -cne $sentinelHash) {
        throw 'An existing cache lock file must not be overwritten or deleted after rejection.'
    }

    $sharedCache = Join-Path $testRoot 'shared-central.bin'
    Configure-Fixture 'cache-owner'
    $null = Invoke-DownloadCase 'cache-owner' -CachePath $sharedCache
    if (-not [IO.File]::Exists($sharedCache)) { throw 'The valid cache fixture was not created.' }
    Configure-Fixture 'cache-owner'
    $null = Invoke-DownloadCase 'cache-reuse' -Id 'cache-owner' -CachePath $sharedCache
    Configure-Fixture 'different-source' -Archive 'reordered'
    $null = Invoke-DownloadCase 'different-source' -Archive 'reordered' -CachePath $sharedCache -Expect failure -PreserveCache
    # A rejected cache and its sidecar remain available for deliberate user recovery.
    $corruptCache = Join-Path $testRoot 'corrupt-central.bin'
    Configure-Fixture 'corrupt-cache-owner'
    $null = Invoke-DownloadCase 'corrupt-cache-prime' -Id 'corrupt-cache-owner' -CachePath $corruptCache
    $cacheBytes = [IO.File]::ReadAllBytes($corruptCache)
    $cacheBytes[0] = $cacheBytes[0] -bxor 1
    [IO.File]::WriteAllBytes($corruptCache, $cacheBytes)
    Configure-Fixture 'corrupt-cache-owner'
    $null = Invoke-DownloadCase 'corrupt-cache' -Id 'corrupt-cache-owner' -CachePath $corruptCache -Expect failure -PreserveCache

    $mutableCache = Join-Path $testRoot 'mutable-central.bin'
    Configure-Fixture 'mutable'
    $null = Invoke-DownloadCase 'mutable-prime' -Id 'mutable' -CachePath $mutableCache
    Configure-Fixture 'mutable' -Archive 'reordered' -Revision 2
    $null = Invoke-DownloadCase 'changed-validator' -Id 'mutable' -Archive 'reordered' -CachePath $mutableCache -Expect failure -PreserveCache
    $legacyCache = Join-Path $testRoot 'legacy-partial-central.bin'
    $central = [Convert]::FromBase64String($metadata.stored.central_base64)
    [IO.File]::WriteAllBytes($legacyCache, $central[0..([int]($central.Length / 2) - 1)])
    Configure-Fixture 'legacy-partial' -Archive 'reordered'
    $null = Invoke-DownloadCase 'legacy-partial' -Archive 'reordered' -CachePath $legacyCache -Expect failure -PreserveCache

    $resumeCache = Join-Path $testRoot 'resume-central.bin'
    Configure-Fixture 'resume' -Archive 'large_directory' -Mode 'interrupt_cache'
    $null = Invoke-DownloadCase 'resume-interrupted' -Id 'resume' -Archive 'large_directory' -CachePath $resumeCache -Expect failure -Attempts 1
    if ((Get-Item -LiteralPath $resumeCache).Length -ne 65536) {
        throw 'An interrupted central-directory download must retain its committed first chunk.'
    }
    Configure-Fixture 'resume' -Archive 'large_directory'
    $resumed = Invoke-DownloadCase 'resume-completed' -Id 'resume' -Archive 'large_directory' -CachePath $resumeCache
    $directoryMeta = $metadata.large_directory
    $resumedRange = 'bytes={0}-{1}' -f ($directoryMeta.central_offset + 65536), ($directoryMeta.central_offset + $directoryMeta.central_size - 1)
    $firstRange = 'bytes={0}-{1}' -f $directoryMeta.central_offset, ($directoryMeta.central_offset + 65535)
    if (@($resumed.requests | Where-Object range -eq $resumedRange).Count -ne 1 -or
        @($resumed.requests | Where-Object range -eq $firstRange).Count -ne 0) {
        throw 'A valid cached prefix must resume at its committed length rather than download the prefix again.'
    }

    if ($IsWindows) {
        foreach ($pathCase in @(
            @{ name = 'root-relative-output'; output = '\remote-zip-test-must-not-exist.jar' },
            @{ name = 'drive-relative-output'; output = 'D:remote-zip-test-must-not-exist.jar' }
        )) {
            Configure-Fixture $pathCase.name
            $null = Invoke-DownloadCase $pathCase.name -OutputPath $pathCase.output -InvalidOutputPath -Expect failure -NoHttp
        }
        foreach ($pathCase in @(
            @{ name = 'root-relative-cache'; cache = '\remote-zip-test-must-not-exist.bin' },
            @{ name = 'drive-relative-cache'; cache = 'D:remote-zip-test-must-not-exist.bin' }
        )) {
            Configure-Fixture $pathCase.name
            $null = Invoke-DownloadCase $pathCase.name -CachePath $pathCase.cache -Expect failure -NoHttp
        }
    }
    Write-Host "PASS: $($results.Count) isolated downloader cases. Evidence: $testRoot"
} finally {
    [IO.File]::WriteAllText((Join-Path $testRoot 'results.json'), (ConvertTo-Json -InputObject @($results.ToArray()) -Depth 10))
    if ($null -ne $server) {
        if (-not $server.HasExited) { $server.Kill($true); $server.WaitForExit() }
        $server.Dispose()
    }
}
