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

    [string] $CentralDirectoryCachePath,

    [ValidateRange(65536, 4194304)]
    [int] $ChunkSize = 524288,

    [ValidateRange(1, 10)]
    [int] $MaxAttempts = 5
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http
Add-Type -AssemblyName System.IO.Compression

$handler = [System.Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $true
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromMinutes(5)
$resolvedUri = $Uri

function Get-RemoteRangeChunk {
    param(
        [long] $Start,
        [long] $End
    )

    for ($attempt = 1; $attempt -le $MaxAttempts; ++$attempt) {
        $request = [System.Net.Http.HttpRequestMessage]::new(
            [System.Net.Http.HttpMethod]::Get,
            $resolvedUri
        )
        $request.Headers.Range = [System.Net.Http.Headers.RangeHeaderValue]::new($Start, $End)
        $response = $null

        try {
            $response = $client.Send($request)
            try {
                if ($response.StatusCode -ne [System.Net.HttpStatusCode]::PartialContent) {
                    throw "Server did not honor byte range $Start-$End (HTTP $([int]$response.StatusCode))."
                }

                $contentRange = $response.Content.Headers.ContentRange
                if ($null -eq $contentRange -or
                    $contentRange.From -ne $Start -or
                    $contentRange.To -ne $End) {
                    throw "Unexpected Content-Range '$contentRange' for requested bytes $Start-$End."
                }

                $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
                $expectedLength = $End - $Start + 1
                if ($bytes.LongLength -ne $expectedLength) {
                    throw "Range length mismatch: expected $expectedLength, got $($bytes.LongLength)."
                }

                return ,$bytes
            }
            finally {
                if ($null -ne $response) {
                    $response.Dispose()
                }
            }
        }
        catch {
            if ($attempt -eq $MaxAttempts) {
                throw
            }

            $delaySeconds = [Math]::Min(8, [Math]::Pow(2, $attempt - 1))
            Write-Warning "Range $Start-$End failed on attempt $attempt; retrying in $delaySeconds second(s): $_"
            Start-Sleep -Seconds $delaySeconds
        }
        finally {
            $request.Dispose()
        }
    }
}

function Get-RemoteRange {
    param(
        [long] $Start,
        [long] $End
    )

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

    $resolvedCache = [IO.Path]::GetFullPath((Join-Path (Get-Location) $CachePath))
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($resolvedCache)) | Out-Null
    $expectedLength = $End - $Start + 1
    $existingLength = if ([IO.File]::Exists($resolvedCache)) {
        [IO.FileInfo]::new($resolvedCache).Length
    }
    else {
        0
    }

    if ($existingLength -gt $expectedLength) {
        throw "Cached range is larger than expected: $existingLength > $expectedLength."
    }

    if ($existingLength -lt $expectedLength) {
        $stream = [IO.File]::Open($resolvedCache, [IO.FileMode]::Append, [IO.FileAccess]::Write, [IO.FileShare]::Read)
        try {
            for ($position = $Start + $existingLength; $position -le $End; $position += $ChunkSize) {
                $chunkEnd = [Math]::Min($End, $position + $ChunkSize - 1)
                $chunk = Get-RemoteRangeChunk -Start $position -End $chunkEnd
                $stream.Write($chunk, 0, $chunk.Length)
                $stream.Flush()
                $downloaded = $stream.Length
                Write-Host "Cached central directory: $downloaded / $expectedLength bytes"
            }
        }
        finally {
            $stream.Dispose()
        }
    }

    $result = [IO.File]::ReadAllBytes($resolvedCache)
    if ($result.LongLength -ne $expectedLength) {
        throw "Cached range length mismatch: expected $expectedLength, got $($result.LongLength)."
    }

    return ,$result
}

function Find-SignatureBackward {
    param(
        [byte[]] $Bytes,
        [byte[]] $Signature
    )

    for ($index = $Bytes.Length - $Signature.Length; $index -ge 0; --$index) {
        $matches = $true
        for ($part = 0; $part -lt $Signature.Length; ++$part) {
            if ($Bytes[$index + $part] -ne $Signature[$part]) {
                $matches = $false
                break
            }
        }

        if ($matches) {
            return $index
        }
    }

    return -1
}

try {
    $headRequest = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::Head,
        $Uri
    )
    try {
        $headResponse = $client.Send($headRequest)
        try {
            $headResponse.EnsureSuccessStatusCode() | Out-Null
            $resolvedUri = $headResponse.RequestMessage.RequestUri
            $archiveLength = $headResponse.Content.Headers.ContentLength
            if ($null -eq $archiveLength -or $archiveLength -le 0) {
                throw "Remote ZIP did not provide Content-Length."
            }
        }
        finally {
            $headResponse.Dispose()
        }
    }
    finally {
        $headRequest.Dispose()
    }

    $tailLength = [Math]::Min([long]65557, $archiveLength)
    $tailStart = $archiveLength - $tailLength
    $tail = Get-RemoteRange -Start $tailStart -End ($archiveLength - 1)
    $eocdIndex = Find-SignatureBackward -Bytes $tail -Signature ([byte[]](0x50, 0x4B, 0x05, 0x06))
    if ($eocdIndex -lt 0) {
        throw "ZIP end-of-central-directory record was not found."
    }

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
            -CachePath $CentralDirectoryCachePath
    }

    $entry = $null
    $offset = 0
    while ($offset -lt $centralDirectory.Length) {
        if ([BitConverter]::ToUInt32($centralDirectory, $offset) -ne 0x02014B50) {
            throw "Invalid central-directory record at offset $offset."
        }

        $compressionMethod = [BitConverter]::ToUInt16($centralDirectory, $offset + 10)
        $compressedSize32 = [BitConverter]::ToUInt32($centralDirectory, $offset + 20)
        $uncompressedSize32 = [BitConverter]::ToUInt32($centralDirectory, $offset + 24)
        $nameLength = [BitConverter]::ToUInt16($centralDirectory, $offset + 28)
        $extraLength = [BitConverter]::ToUInt16($centralDirectory, $offset + 30)
        $commentLength = [BitConverter]::ToUInt16($centralDirectory, $offset + 32)
        $localHeaderOffset32 = [BitConverter]::ToUInt32($centralDirectory, $offset + 42)
        $name = [Text.Encoding]::UTF8.GetString($centralDirectory, $offset + 46, $nameLength)

        if ($name -eq $EntryName) {
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
                    $headerId = [BitConverter]::ToUInt16($centralDirectory, $extraOffset + $extraCursor)
                    $dataSize = [BitConverter]::ToUInt16($centralDirectory, $extraOffset + $extraCursor + 2)
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

    $localHeader = Get-RemoteRange `
        -Start $entry.LocalHeaderOffset `
        -End ($entry.LocalHeaderOffset + 29)
    if ([BitConverter]::ToUInt32($localHeader, 0) -ne 0x04034B50) {
        throw "Invalid local header for '$EntryName'."
    }

    $localNameLength = [BitConverter]::ToUInt16($localHeader, 26)
    $localExtraLength = [BitConverter]::ToUInt16($localHeader, 28)
    $dataOffset = $entry.LocalHeaderOffset + 30 + $localNameLength + $localExtraLength
    $compressed = Get-RemoteRange `
        -Start $dataOffset `
        -End ($dataOffset + $entry.CompressedSize - 1)

    if ($entry.CompressionMethod -eq 0) {
        $uncompressed = $compressed
    }
    elseif ($entry.CompressionMethod -eq 8) {
        $input = [IO.MemoryStream]::new($compressed, $false)
        $output = [IO.MemoryStream]::new()
        try {
            $deflate = [IO.Compression.DeflateStream]::new(
                $input,
                [IO.Compression.CompressionMode]::Decompress
            )
            try {
                $deflate.CopyTo($output)
            }
            finally {
                $deflate.Dispose()
            }
            $uncompressed = $output.ToArray()
        }
        finally {
            $output.Dispose()
            $input.Dispose()
        }
    }
    else {
        throw "Unsupported ZIP compression method $($entry.CompressionMethod)."
    }

    if ($uncompressed.Length -ne $entry.UncompressedSize) {
        throw "Extracted size mismatch: expected $($entry.UncompressedSize), got $($uncompressed.Length)."
    }

    $resolvedOutput = [IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputPath))
    $outputDirectory = [IO.Path]::GetDirectoryName($resolvedOutput)
    [IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
    [IO.File]::WriteAllBytes($resolvedOutput, $uncompressed)

    Get-FileHash -LiteralPath $resolvedOutput -Algorithm SHA256 |
        Select-Object Path, Hash
}
finally {
    $client.Dispose()
    $handler.Dispose()
}
