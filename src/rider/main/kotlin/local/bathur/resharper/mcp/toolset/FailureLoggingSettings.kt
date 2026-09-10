// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.
package local.bathur.resharper.mcp.toolset

import com.intellij.openapi.options.advanced.AdvancedSettings

internal object FailureLoggingSettings {
    const val ID = "local.bathur.resharper.mcp.toolset.failure.logging"

    fun isEnabled(): Boolean = AdvancedSettings.getBoolean(ID)
}
