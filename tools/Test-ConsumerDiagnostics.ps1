# Copyright (C) 2026 Bathur.
# Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
# See LICENSE and LICENSING.md in the public source root.

param(
    [Parameter(Mandatory = $true)][string] $RiderHome,
    [Parameter(Mandatory = $true)][string] $BackendPath
)

$ErrorActionPreference = 'Stop'
$diagnosticTestAssemblies = [IO.Path]::Combine($RiderHome, 'lib', 'ReSharperHost')
[AppDomain]::CurrentDomain.add_AssemblyResolve({
    param($sender, $eventArgs)
    # Keep resolution free of PowerShell cmdlets: module autoload can recurse into AssemblyResolve.
    $dependencyFile = [IO.Path]::Combine($diagnosticTestAssemblies, ([Reflection.AssemblyName]::new($eventArgs.Name)).Name + '.dll')
    if ([IO.File]::Exists($dependencyFile)) { return [Reflection.Assembly]::LoadFrom($dependencyFile) }
    return $null
})

$backend = [Reflection.Assembly]::LoadFrom([IO.Path]::GetFullPath($BackendPath))
$cppServices = [Reflection.Assembly]::LoadFrom([IO.Path]::Combine($diagnosticTestAssemblies, 'JetBrains.ReSharper.Feature.Services.Cpp.dll'))
$engineType = $backend.GetType('Bathur.ReSharperMcpToolset.CppSemanticEngine', $true)
$staticFlags = [Reflection.BindingFlags]'NonPublic,Static'
$instanceFlags = [Reflection.BindingFlags]'NonPublic,Instance'

$messageMethod = $engineType.GetMethod('UnsupportedReferencesMessage', $staticFlags)
$assetMessage = $messageMethod.Invoke($null, [object[]]@(7, 0))
if ($assetMessage -ne '7 Unreal asset reference(s) are not included in these source results.') {
    throw 'Asset omissions must produce one concise, actionable sentence.'
}
$otherMessage = $messageMethod.Invoke($null, [object[]]@(0, 2))
$mixedMessage = $messageMethod.Invoke($null, [object[]]@(7, 2))
if ($otherMessage.Contains('Unreal') -or -not $otherMessage.Contains('2 unsupported') -or
    -not $mixedMessage.Contains('7 Unreal') -or -not $mixedMessage.Contains('2 unsupported')) {
    throw 'Unsupported reference categories were lost or mislabeled.'
}

$relationMethod = $engineType.GetMethod('RelatedSymbolRelation', $staticFlags)
foreach ($case in @(
    @($true, $true, 'direct'), @($true, $false, 'direct'),
    @($false, $true, 'indirect'), @($false, $false, 'unknown')
)) {
    if ($relationMethod.Invoke($null, [object[]]@($case[0], $case[1])) -ne $case[2]) {
        throw 'Unverified direct relationships must not be reported as indirect.'
    }
}

# Use inert instances of the locked SDK types. No IDE, solution, PSI or file mutation is involved.
$assetType = $cppServices.GetType('JetBrains.ReSharper.Feature.Services.Cpp.UE4.UEAsset.Search.UnrealAssetOccurence', $true)
$cppOccurrenceType = $cppServices.GetType('JetBrains.ReSharper.Feature.Services.Cpp.Occurrences.CppDeclaredElementOccurrence', $true)
$asset = [Runtime.CompilerServices.RuntimeHelpers]::GetUninitializedObject($assetType)
$unavailableCpp = [Runtime.CompilerServices.RuntimeHelpers]::GetUninitializedObject($cppOccurrenceType)
$occurrencesMethod = $engineType.GetMethod('GetCppOccurrenceElements', $staticFlags)
$occurrenceType = $occurrencesMethod.GetParameters()[0].ParameterType.GetGenericArguments()[0]
$occurrences = [Array]::CreateInstance($occurrenceType, 3)
$occurrences.SetValue($asset, 0)
$occurrences.SetValue($unavailableCpp, 1)
# Entry 2 stays null: an unknown/missing result must not silently become a scope exclusion.
$occurrenceArguments = [object[]]@($occurrences, 0)
$returned = $occurrencesMethod.Invoke($null, $occurrenceArguments)
if ($returned.Length -ne 0 -or $occurrenceArguments[1] -ne 2) {
    throw 'Unavailable C++ and unknown results must be counted; known asset results must be excluded.'
}

$qualifierType = $cppServices.GetType('JetBrains.ReSharper.Feature.Services.Cpp.CodeStructure.CppCodeStructureQualifierElement', $true)
$truncationType = $cppServices.GetType('JetBrains.ReSharper.Feature.Services.Cpp.CodeStructure.CppTooManyElementsCodeStructureElement', $true)
$group = [Runtime.CompilerServices.RuntimeHelpers]::GetUninitializedObject($qualifierType)
$child = [Runtime.CompilerServices.RuntimeHelpers]::GetUninitializedObject($truncationType)
$childrenField = $qualifierType.BaseType.GetField('myChildren', $instanceFlags)
$children = [Activator]::CreateInstance($childrenField.FieldType)
$children.Add($child)
$childrenField.SetValue($group, $children)
$collectMethod = $engineType.GetMethod('CollectFileSymbols', $instanceFlags)
$lifetimeType = $collectMethod.GetParameters()[0].ParameterType
$eternalField = $lifetimeType.GetField('Eternal', [Reflection.BindingFlags]'Public,Static')
$eternal = if ($null -ne $eternalField) { $eternalField.GetValue($null) } else { $lifetimeType.GetProperty('Eternal').GetValue($null) }
$mapped = [Activator]::CreateInstance($collectMethod.GetParameters()[5].ParameterType)
$collectArguments = [object[]]@($eternal, $group, 'unused.cpp', -1, -1, $mapped, 0, 0, $false)
$engine = [Runtime.CompilerServices.RuntimeHelpers]::GetUninitializedObject($engineType)
$null = $collectMethod.Invoke($engine, $collectArguments)
if ($mapped.Count -ne 0 -or $collectArguments[6] -ne 0 -or $collectArguments[7] -ne 0 -or -not $collectArguments[8]) {
    throw 'Qualifier groups must not count as missing declarations, and their children must still be visited.'
}

'PASS: asset/unsupported messages, relation certainty, missing C++ result accounting, and qualifier child traversal.'
'These isolated checks do not replace live Rider semantic validation.'
