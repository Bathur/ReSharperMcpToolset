// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

package local.bathur.resharper.mcp.toolset

import java.nio.file.Path

internal data class ExistingCppRegistrationPaths(val parentDirectory: Path, val filePath: Path) {
    val isStrictDescendant: Boolean
        get() = filePath != parentDirectory && filePath.startsWith(parentDirectory)

    companion object {
        fun resolve(parentDirectory: Path, filePath: Path): ExistingCppRegistrationPaths =
            ExistingCppRegistrationPaths(parentDirectory.toRealPath(), filePath.toRealPath())
    }
}
