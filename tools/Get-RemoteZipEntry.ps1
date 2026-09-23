# Copyright (C) 2026 Bathur.
# Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
# See LICENSE and LICENSING.md in the public source root.

param(
    [Parameter(Mandatory = $true)]
    [uri] $Uri,

    [Parameter(Mandatory = $true)]
    [string] $EntryName,

    [Parameter(Mandatory = $true)]
    [string] $OutputPath,

    [Parameter(Mandatory = $true)]
    [ValidateRange(0, 2147483647)]
    [long] $ExpectedSize,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-fA-F]{64}$')]
    [string] $ExpectedSha256,

    [string] $CentralDirectoryCachePath,

    [ValidateRange(65536, 4194304)]
    [int] $ChunkSize = 524288,

    [ValidateRange(1, 10)]
    [int] $MaxAttempts = 5,

    [ValidateRange(1, 600)]
    [int] $RangeTimeoutSeconds = 60
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http
Add-Type -AssemblyName System.IO.Compression

function Resolve-DownloadPath {
    param([string] $Path)
    if ([string]::IsNullOrWhiteSpace($Path)) { throw "A file path must not be empty." }
    if ([IO.Path]::IsPathFullyQualified($Path)) { return [IO.Path]::GetFullPath($Path) }
    if ([IO.Path]::IsPathRooted($Path) -or $Path -match '^[A-Za-z]:') {
        throw "Root-relative and drive-relative paths are not supported: '$Path'."
    }
    return [IO.Path]::GetFullPath($Path, $ExecutionContext.SessionState.Path.CurrentFileSystemLocation.Path)
}

if (-not $Uri.IsAbsoluteUri -or $Uri.Scheme -notin @('http', 'https')) {
    throw "Uri must be an absolute HTTP or HTTPS address."
}
if ([string]::IsNullOrEmpty($EntryName)) { throw "EntryName must not be empty." }
$resolvedOutput = Resolve-DownloadPath $OutputPath
$resolvedCache = if ([string]::IsNullOrWhiteSpace($CentralDirectoryCachePath)) { $null } else {
    Resolve-DownloadPath $CentralDirectoryCachePath
}
$cacheMetadataPath = if ($null -eq $resolvedCache) { $null } else { $resolvedCache + '.metadata.json' }
$cacheLockPath = if ($null -eq $resolvedCache) { $null } else { $resolvedCache + '.lock' }
$pathComparer = if ([OperatingSystem]::IsWindows()) { [StringComparer]::OrdinalIgnoreCase } else { [StringComparer]::Ordinal }
$destinations = @($resolvedOutput, $resolvedCache, $cacheMetadataPath, $cacheLockPath) |
    Where-Object { $null -ne $_ }
$seenPaths = [Collections.Generic.HashSet[string]]::new($pathComparer)
foreach ($destination in $destinations) {
    if (-not $seenPaths.Add($destination)) { throw "Output, cache and cache metadata paths must be distinct." }
    if ([IO.Directory]::Exists($destination)) { throw "A destination is a directory: '$destination'." }
}

$handler = [System.Net.Http.SocketsHttpHandler]::new()
$handler.AllowAutoRedirect = $true
$handler.AutomaticDecompression = [Net.DecompressionMethods]::None
# Disposing a rejected response must not drain its large body for connection reuse.
$handler.MaxResponseDrainSize = 0
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [Threading.Timeout]::InfiniteTimeSpan
$resolvedUri = $Uri
$archiveLength = 0L
$strongETag = $null
$lastModified = ''
$outputTemporary = $null
$outputStream = $null
$cacheLease = $null

function Stop-RangeProtocol {
    param([string] $Message)
    throw [IO.InvalidDataException]::new($Message)
}

function Get-StrongETag {
    param($Response)
    $tag = $Response.Headers.ETag
    if ($null -ne $tag -and -not $tag.IsWeak) { return $tag.ToString() }
    return $null
}

function Assert-IdentityEncoding {
    param($Response)
    foreach ($encoding in $Response.Content.Headers.ContentEncoding) {
        if (-not [string]::Equals($encoding, 'identity', [StringComparison]::OrdinalIgnoreCase)) {
            Stop-RangeProtocol "Encoded HTTP bodies are not supported for ZIP byte ranges."
        }
    }
}

function Get-StreamSha256 {
    param([IO.Stream] $Stream)
    $savedPosition = $Stream.Position
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        $Stream.Position = 0
        return [Convert]::ToHexString($algorithm.ComputeHash($Stream))
    }
    finally {
        $Stream.Position = $savedPosition
        $algorithm.Dispose()
    }
}

function Move-VerifiedFile {
    param([string] $Temporary, [string] $Destination)
    if ([IO.File]::Exists($Destination)) {
        [IO.File]::Replace($Temporary, $Destination, [NullString]::Value)
    }
    else {
        [IO.File]::Move($Temporary, $Destination)
    }
}

function Get-RemoteRangeChunk {
    param(
        [long] $Start,
        [long] $End
    )

    if ($Start -lt 0 -or $End -lt $Start -or $End -ge $archiveLength) {
        Stop-RangeProtocol "Invalid ZIP byte range $Start-$End for archive length $archiveLength."
    }
    $expectedLength = $End - $Start + 1
    if ($expectedLength -gt $ChunkSize) { Stop-RangeProtocol "The requested chunk exceeds ChunkSize." }

    for ($attempt = 1; $attempt -le $MaxAttempts; ++$attempt) {
        $request = [System.Net.Http.HttpRequestMessage]::new(
            [System.Net.Http.HttpMethod]::Get,
            $resolvedUri
        )
        $request.Headers.Range = [System.Net.Http.Headers.RangeHeaderValue]::new($Start, $End)
        $request.Headers.AcceptEncoding.ParseAdd('identity')
        if ($null -ne $strongETag) { $request.Headers.IfMatch.ParseAdd($strongETag) }
        $response = $null
        $body = $null
        $deadline = [Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds($RangeTimeoutSeconds))

        try {
            $response = $client.SendAsync(
                $request, [Net.Http.HttpCompletionOption]::ResponseHeadersRead, $deadline.Token
            ).GetAwaiter().GetResult()
            $status = [int]$response.StatusCode
            if ($status -in @(408, 429, 500, 502, 503, 504)) {
                throw [Net.Http.HttpRequestException]::new("Transient HTTP $status for range $Start-$End.")
            }
            if ($status -ne 206) {
                Stop-RangeProtocol "Server did not honor byte range $Start-$End (HTTP $status)."
            }
            Assert-IdentityEncoding $response
            if (-not [string]::Equals($response.RequestMessage.RequestUri.AbsoluteUri, $resolvedUri.AbsoluteUri, [StringComparison]::Ordinal)) {
                Stop-RangeProtocol "The resolved archive URI changed during range retrieval."
            }
            $contentRange = $response.Content.Headers.ContentRange
            if ($null -eq $contentRange -or -not [string]::Equals($contentRange.Unit, 'bytes', [StringComparison]::OrdinalIgnoreCase) -or
                $contentRange.From -ne $Start -or $contentRange.To -ne $End -or
                $contentRange.Length -ne $archiveLength) {
                Stop-RangeProtocol "Unexpected Content-Range '$contentRange' for requested bytes $Start-$End."
            }
            $contentLength = $response.Content.Headers.ContentLength
            if ($null -ne $contentLength -and $contentLength -ne $expectedLength) {
                Stop-RangeProtocol "Unexpected Content-Length '$contentLength'; expected $expectedLength."
            }
            $responseETag = Get-StrongETag $response
            if ($null -ne $strongETag -and -not [string]::Equals($responseETag, $strongETag, [StringComparison]::Ordinal)) {
                Stop-RangeProtocol "The strong ETag changed or disappeared during range retrieval."
            }

            # The same deadline covers both headers and every body read. One extra byte
            # detects oversized chunked/unknown-length responses without buffering them.
            $bytes = [byte[]]::new([int]$expectedLength + 1)
            $body = $response.Content.ReadAsStreamAsync($deadline.Token).GetAwaiter().GetResult()
            $read = 0
            while ($read -lt $bytes.Length) {
                $count = $body.ReadAsync($bytes, $read, $bytes.Length - $read, $deadline.Token).GetAwaiter().GetResult()
                if ($count -eq 0) { break }
                $read += $count
            }
            if ($read -ne $expectedLength) {
                Stop-RangeProtocol "Range length mismatch: expected $expectedLength, got $read."
            }
            if ($null -eq $strongETag -and $null -ne $responseETag) {
                $script:strongETag = $responseETag
            }
            $result = [byte[]]::new([int]$expectedLength)
            [Array]::Copy($bytes, $result, $result.Length)
            return ,$result
        }
        catch {
            $cause = $_.Exception.GetBaseException()
            $retryable = $cause -is [Net.Http.HttpRequestException] -or
                $cause -is [IO.IOException] -or
                ($cause -is [OperationCanceledException] -and $deadline.IsCancellationRequested)
            if ($cause -is [IO.InvalidDataException] -or -not $retryable -or $attempt -eq $MaxAttempts) {
                throw
            }

            $delaySeconds = [Math]::Min(8, [Math]::Pow(2, $attempt - 1))
            Write-Warning "Range $Start-$End failed on attempt $attempt; retrying in $delaySeconds second(s): $_"
            Start-Sleep -Seconds $delaySeconds
        }
        finally {
            $deadline.Cancel()
            if ($null -ne $body) { $body.Dispose() }
            if ($null -ne $response) { $response.Dispose() }
            $deadline.Dispose()
            $request.Dispose()
        }
    }
}

function Get-RemoteRange {
    param(
        [long] $Start,
        [long] $End
    )

    if ($End -eq $Start - 1 -and $Start -ge 0 -and $Start -le $archiveLength) {
        return ,([byte[]]::new(0))
    }
    if ($Start -lt 0 -or $End -lt $Start -or $End -ge $archiveLength -or
        ($End - $Start + 1) -gt [int]::MaxValue) {
        Stop-RangeProtocol "Invalid or unsupported in-memory ZIP range $Start-$End."
    }
    $output = [IO.MemoryStream]::new()
    try {
        for ($position = $Start; $position -le $End; $position += $ChunkSize) {
            $chunkEnd = [Math]::Min($End, $position + $ChunkSize - 1)
            $chunk = Get-RemoteRangeChunk -Start $position -End $chunkEnd
            $output.Write($chunk, 0, $chunk.Length)
        }

        return ,($output.ToArray())
    }
    finally {
        $output.Dispose()
    }
}

function Get-CachedRemoteRange {
    param(
        [long] $Start,
        [long] $End,
        [string] $CachePath
    )

    if ([string]::IsNullOrEmpty($strongETag)) {
        throw "A strong ETag is required for central-directory caching. Omit CentralDirectoryCachePath for a fresh, hash-verified extraction."
    }
    $expectedLength = $End - $Start + 1
    if ($Start -lt 0 -or $expectedLength -lt 0 -or $expectedLength -gt [int]::MaxValue -or $End -ge $archiveLength) {
        Stop-RangeProtocol "Invalid cached central-directory range."
    }
    $identity = [ordered]@{
        SchemaVersion = 1
        RequestedUri = $Uri.AbsoluteUri
        ResolvedUri = $resolvedUri.AbsoluteUri
        ArchiveLength = $archiveLength
        StrongETag = $strongETag
        LastModified = $lastModified
        RangeStart = $Start
        RangeLength = $expectedLength
    }
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($CachePath)) | Out-Null
    $script:cacheLease = [IO.FileStream]::new(
        $cacheLockPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::ReadWrite,
        [IO.FileShare]::None, 1, [IO.FileOptions]::DeleteOnClose
    )
    $exists = [IO.File]::Exists($CachePath)
    if ($exists -ne [IO.File]::Exists($cacheMetadataPath)) {
        throw "Cache and provenance metadata must both exist. Preserve the old cache and choose a new CentralDirectoryCachePath."
    }
    $stream = $null
    try {
        if ($exists) {
            try {
                $metadata = [IO.File]::ReadAllText($cacheMetadataPath) | ConvertFrom-Json
                foreach ($key in $identity.Keys) {
                    if (-not [string]::Equals([string]$metadata.Identity.$key, [string]$identity[$key], [StringComparison]::Ordinal)) {
                        throw "Cache identity field '$key' differs."
                    }
                }
                $stream = [IO.File]::Open($CachePath, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::Read)
                if ($stream.Length -ne [long]$metadata.CommittedLength -or $stream.Length -gt $expectedLength -or
                    -not [string]::Equals((Get-StreamSha256 $stream), [string]$metadata.PrefixSha256, [StringComparison]::OrdinalIgnoreCase)) {
                    throw "Cache length or content digest differs from its committed metadata."
                }
            }
            catch { throw "Untrusted central-directory cache; choose a new cache path. $($_.Exception.Message)" }
        }
        else {
            $stream = [IO.File]::Open($CachePath, [IO.FileMode]::CreateNew, [IO.FileAccess]::ReadWrite, [IO.FileShare]::Read)
            Write-CacheMetadata $identity $stream
        }

        $stream.Position = $stream.Length
        for ($position = $Start + $stream.Length; $position -le $End; $position += $ChunkSize) {
            $chunkEnd = [Math]::Min($End, $position + $ChunkSize - 1)
            $chunk = Get-RemoteRangeChunk -Start $position -End $chunkEnd
            $stream.Write($chunk, 0, $chunk.Length)
            $stream.Flush($true)
            Write-CacheMetadata $identity $stream
            Write-Host "Cached central directory: $($stream.Length) / $expectedLength bytes"
        }
        $result = [byte[]]::new([int]$expectedLength)
        $stream.Position = 0
        $read = 0
        while ($read -lt $result.Length) {
            $count = $stream.Read($result, $read, $result.Length - $read)
            if ($count -eq 0) { throw "Cached range ended before the expected length." }
            $read += $count
        }
        return ,$result
    }
    finally {
        if ($null -ne $stream) { $stream.Dispose() }
        $script:cacheLease.Dispose()
        $script:cacheLease = $null
    }
}

function Write-CacheMetadata {
    param($Identity, [IO.Stream] $Stream)
    $record = [ordered]@{
        Identity = $Identity
        CommittedLength = $Stream.Length
        PrefixSha256 = Get-StreamSha256 $Stream
    }
    $temporary = $cacheMetadataPath + '.' + [Guid]::NewGuid().ToString('N') + '.tmp'
    try {
        [IO.File]::WriteAllText($temporary, ($record | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
        Move-VerifiedFile $temporary $cacheMetadataPath
    }
    finally { if ([IO.File]::Exists($temporary)) { [IO.File]::Delete($temporary) } }
}

function Find-SignatureBackward {
    param(
        [byte[]] $Bytes,
        [byte[]] $Signature
    )

    for ($index = $Bytes.Length - $Signature.Length; $index -ge 0; --$index) {
        $signatureMatches = $true
        for ($part = 0; $part -lt $Signature.Length; ++$part) {
            if ($Bytes[$index + $part] -ne $Signature[$part]) {
                $signatureMatches = $false
                break
            }
        }

        if ($signatureMatches) {
            return $index
        }
    }

    return -1
}

try {
    # Resolve/check destinations and create the private staging file before networking.
    $outputDirectory = [IO.Path]::GetDirectoryName($resolvedOutput)
    [IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
    $outputTemporary = [IO.Path]::Combine(
        $outputDirectory, '.' + [IO.Path]::GetFileName($resolvedOutput) + '.' + [Guid]::NewGuid().ToString('N') + '.tmp'
    )
    $outputStream = [IO.File]::Open($outputTemporary, [IO.FileMode]::CreateNew, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    $headRequest = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::Head,
        $Uri
    )
    $headRequest.Headers.AcceptEncoding.ParseAdd('identity')
    $headDeadline = [Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds($RangeTimeoutSeconds))
    $headResponse = $null
    try {
        $headResponse = $client.SendAsync(
            $headRequest, [Net.Http.HttpCompletionOption]::ResponseHeadersRead, $headDeadline.Token
        ).GetAwaiter().GetResult()
        try {
            $headResponse.EnsureSuccessStatusCode() | Out-Null
            Assert-IdentityEncoding $headResponse
            $resolvedUri = $headResponse.RequestMessage.RequestUri
            $archiveLength = $headResponse.Content.Headers.ContentLength
            if ($null -eq $archiveLength -or $archiveLength -le 0) {
                throw "Remote ZIP did not provide Content-Length."
            }
            $archiveLength = [long]$archiveLength
            $strongETag = Get-StrongETag $headResponse
            if ($null -ne $headResponse.Content.Headers.LastModified) {
                $lastModified = $headResponse.Content.Headers.LastModified.ToString('R', [Globalization.CultureInfo]::InvariantCulture)
            }
        }
        finally {
            $headResponse.Dispose()
        }
    }
    finally {
        $headDeadline.Cancel()
        if ($null -ne $headResponse) { $headResponse.Dispose() }
        $headDeadline.Dispose()
        $headRequest.Dispose()
    }

    $tailLength = [Math]::Min([long]65557, $archiveLength)
    $tailStart = $archiveLength - $tailLength
    $tail = Get-RemoteRange -Start $tailStart -End ($archiveLength - 1)
    $eocdIndex = Find-SignatureBackward -Bytes $tail -Signature ([byte[]](0x50, 0x4B, 0x05, 0x06))
    if ($eocdIndex -lt 0) {
        throw "ZIP end-of-central-directory record was not found."
    }
    if ($eocdIndex + 22 -gt $tail.Length) { throw "Truncated ZIP end-of-central-directory record." }

    $centralDirectorySize = [BitConverter]::ToUInt32($tail, $eocdIndex + 12)
    $centralDirectoryOffset = [BitConverter]::ToUInt32($tail, $eocdIndex + 16)
    if ($centralDirectorySize -eq [uint32]::MaxValue -or $centralDirectoryOffset -eq [uint32]::MaxValue) {
        throw "ZIP64 archives are not supported by this focused downloader."
    }

    if ([string]::IsNullOrWhiteSpace($CentralDirectoryCachePath)) {
        $centralDirectory = Get-RemoteRange `
            -Start $centralDirectoryOffset `
            -End ($centralDirectoryOffset + $centralDirectorySize - 1)
    }
    else {
        $centralDirectory = Get-CachedRemoteRange `
            -Start $centralDirectoryOffset `
            -End ($centralDirectoryOffset + $centralDirectorySize - 1) `
            -CachePath $resolvedCache
    }

    $entry = $null
    $offset = 0
    while ($offset -lt $centralDirectory.Length) {
        if ($centralDirectory.Length - $offset -lt 46) { throw "Truncated central-directory record." }
        if ([BitConverter]::ToUInt32($centralDirectory, $offset) -ne 0x02014B50) {
            throw "Invalid central-directory record at offset $offset."
        }

        $entryFlags = [BitConverter]::ToUInt16($centralDirectory, $offset + 8)
        $compressionMethod = [BitConverter]::ToUInt16($centralDirectory, $offset + 10)
        $compressedSize32 = [BitConverter]::ToUInt32($centralDirectory, $offset + 20)
        $uncompressedSize32 = [BitConverter]::ToUInt32($centralDirectory, $offset + 24)
        $nameLength = [BitConverter]::ToUInt16($centralDirectory, $offset + 28)
        $extraLength = [BitConverter]::ToUInt16($centralDirectory, $offset + 30)
        $commentLength = [BitConverter]::ToUInt16($centralDirectory, $offset + 32)
        $localHeaderOffset32 = [BitConverter]::ToUInt32($centralDirectory, $offset + 42)
        if (46L + $nameLength + $extraLength + $commentLength -gt $centralDirectory.Length - $offset) {
            throw "Central-directory variable fields extend beyond the cached range."
        }
        $name = [Text.Encoding]::UTF8.GetString($centralDirectory, $offset + 46, $nameLength)

        if ([string]::Equals($name, $EntryName, [StringComparison]::Ordinal)) {
            $compressedSize = [long]$compressedSize32
            $uncompressedSize = [long]$uncompressedSize32
            $localHeaderOffset = [long]$localHeaderOffset32

            if ($compressedSize32 -eq [uint32]::MaxValue -or
                $uncompressedSize32 -eq [uint32]::MaxValue -or
                $localHeaderOffset32 -eq [uint32]::MaxValue) {
                $extraOffset = $offset + 46 + $nameLength
                $extraCursor = 0
                $zip64DataOffset = -1
                $zip64DataSize = 0
                while ($extraCursor -lt $extraLength) {
                    if ($extraLength - $extraCursor -lt 4) { throw "Truncated ZIP extra-field header." }
                    $headerId = [BitConverter]::ToUInt16($centralDirectory, $extraOffset + $extraCursor)
                    $dataSize = [BitConverter]::ToUInt16($centralDirectory, $extraOffset + $extraCursor + 2)
                    if ($dataSize -gt $extraLength - $extraCursor - 4) { throw "ZIP extra field exceeds its declared range." }
                    if ($headerId -eq 0x0001) {
                        $zip64DataOffset = $extraOffset + $extraCursor + 4
                        $zip64DataSize = $dataSize
                        break
                    }
                    $extraCursor += 4 + $dataSize
                }

                if ($zip64DataOffset -lt 0) {
                    throw "Entry '$EntryName' uses ZIP64 sentinel values but has no ZIP64 extra field."
                }

                $valueOffset = $zip64DataOffset
                $zip64End = $zip64DataOffset + $zip64DataSize
                if ($uncompressedSize32 -eq [uint32]::MaxValue) {
                    if ($valueOffset + 8 -gt $zip64End) {
                        throw "ZIP64 extra field is missing the uncompressed size for '$EntryName'."
                    }
                    $uncompressedSize = [long][BitConverter]::ToUInt64($centralDirectory, $valueOffset)
                    $valueOffset += 8
                }
                if ($compressedSize32 -eq [uint32]::MaxValue) {
                    if ($valueOffset + 8 -gt $zip64End) {
                        throw "ZIP64 extra field is missing the compressed size for '$EntryName'."
                    }
                    $compressedSize = [long][BitConverter]::ToUInt64($centralDirectory, $valueOffset)
                    $valueOffset += 8
                }
                if ($localHeaderOffset32 -eq [uint32]::MaxValue) {
                    if ($valueOffset + 8 -gt $zip64End) {
                        throw "ZIP64 extra field is missing the local-header offset for '$EntryName'."
                    }
                    $localHeaderOffset = [long][BitConverter]::ToUInt64($centralDirectory, $valueOffset)
                }
            }

            $entry = [pscustomobject]@{
                Flags = $entryFlags
                CompressionMethod = $compressionMethod
                CompressedSize = $compressedSize
                UncompressedSize = $uncompressedSize
                LocalHeaderOffset = $localHeaderOffset
            }
            break
        }

        $offset += 46 + $nameLength + $extraLength + $commentLength
    }

    if ($null -eq $entry) {
        throw "Entry '$EntryName' was not found in the remote ZIP."
    }
    if ($entry.UncompressedSize -ne $ExpectedSize) {
        throw "ZIP entry size does not match the trusted expected size $ExpectedSize."
    }
    if (($entry.Flags -band 0x41) -ne 0) { throw "Encrypted ZIP entries are not supported." }
    if ($entry.CompressionMethod -notin @(0, 8)) { throw "Unsupported ZIP compression method $($entry.CompressionMethod)." }
    if ($entry.CompressionMethod -eq 0 -and $entry.CompressedSize -ne $ExpectedSize) {
        throw "Stored ZIP entry has inconsistent sizes."
    }

    $localHeader = Get-RemoteRange `
        -Start $entry.LocalHeaderOffset `
        -End ($entry.LocalHeaderOffset + 29)
    if ([BitConverter]::ToUInt32($localHeader, 0) -ne 0x04034B50) {
        throw "Invalid local header for '$EntryName'."
    }
    if ([BitConverter]::ToUInt16($localHeader, 6) -ne $entry.Flags -or
        [BitConverter]::ToUInt16($localHeader, 8) -ne $entry.CompressionMethod) {
        throw "Local and central ZIP headers disagree about flags or compression."
    }

    $localNameLength = [BitConverter]::ToUInt16($localHeader, 26)
    $localExtraLength = [BitConverter]::ToUInt16($localHeader, 28)
    $localNameBytes = Get-RemoteRange -Start ($entry.LocalHeaderOffset + 30) -End ($entry.LocalHeaderOffset + 29 + $localNameLength)
    $localName = [Text.Encoding]::UTF8.GetString($localNameBytes)
    if (-not [string]::Equals($localName, $EntryName, [StringComparison]::Ordinal)) {
        throw "Local ZIP header does not name the requested entry '$EntryName'."
    }
    $dataOffset = $entry.LocalHeaderOffset + 30 + $localNameLength + $localExtraLength
    $compressed = Get-RemoteRange `
        -Start $dataOffset `
        -End ($dataOffset + $entry.CompressedSize - 1)

    $input = [IO.MemoryStream]::new($compressed, $false)
    $payload = $input
    try {
        if ($entry.CompressionMethod -eq 8) {
            $payload = [IO.Compression.DeflateStream]::new($input, [IO.Compression.CompressionMode]::Decompress, $true)
        }
        $buffer = [byte[]]::new(65536)
        $written = 0L
        while ($true) {
            $limit = [int][Math]::Min($buffer.Length, $ExpectedSize - $written + 1)
            $count = $payload.Read($buffer, 0, $limit)
            if ($count -eq 0) { break }
            if ($written + $count -gt $ExpectedSize) { throw "Extracted entry exceeds the trusted expected size." }
            $outputStream.Write($buffer, 0, $count)
            $written += $count
        }
        if ($written -ne $ExpectedSize) { throw "Extracted size mismatch: expected $ExpectedSize, got $written." }
    }
    finally {
        if ($payload -ne $input) { $payload.Dispose() }
        $input.Dispose()
    }

    $outputStream.Flush($true)
    $actualHash = Get-StreamSha256 $outputStream
    if (-not [string]::Equals($actualHash, $ExpectedSha256, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Extracted SHA-256 does not match ExpectedSha256; the existing output was preserved."
    }
    $outputStream.Dispose()
    $outputStream = $null
    Move-VerifiedFile $outputTemporary $resolvedOutput
    $outputTemporary = $null
    [pscustomobject]@{ Path = $resolvedOutput; Hash = $actualHash }
}
finally {
    if ($null -ne $outputStream) { $outputStream.Dispose() }
    if ($null -ne $cacheLease) { $cacheLease.Dispose() }
    if ($null -ne $outputTemporary -and [IO.File]::Exists($outputTemporary)) { [IO.File]::Delete($outputTemporary) }
    $client.Dispose()
    $handler.Dispose()
}
