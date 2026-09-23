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

# Invoke the production classifier with the locked SDK's actual element kinds.
# The proxy supplies only GetElementType; this does not construct or validate real C++ PSI.
Add-Type -TypeDefinition @'
using System;
using System.Reflection;

public class ConsumerDiagnosticsDeclaredElementProxy : DispatchProxy
{
    public object ElementType { get; set; }

    protected override object Invoke(MethodInfo method, object[] arguments)
    {
        if (method.Name == "GetElementType" && arguments.Length == 0)
            return ElementType;

        throw new InvalidOperationException("Unexpected declared-element call: " + method.Name);
    }
}
'@
$normalizeKindMethod = $engineType.GetMethod('NormalizeKind', $staticFlags)
$declaredElementType = $normalizeKindMethod.GetParameters()[0].ParameterType
$kindElement = [Reflection.DispatchProxy]::Create($declaredElementType, [ConsumerDiagnosticsDeclaredElementProxy])
$kindProxy = [ConsumerDiagnosticsDeclaredElementProxy]$kindElement
$cppPsi = [Reflection.Assembly]::LoadFrom([IO.Path]::Combine($diagnosticTestAssemblies, 'JetBrains.ReSharper.Cpp.dll'))
$elementKindsType = $cppPsi.GetType('JetBrains.ReSharper.Psi.Cpp.Language.CppDeclaredElementTypes', $true)
foreach ($case in @(
    @('GLOBAL_OPERATOR', '', 'global_operator'),
    @('MEMBER_OPERATOR', 'Owner', 'member_operator'),
    @('CONVERSION_OPERATOR', 'Owner', 'conversion_operator'),
    @('LITERAL_OPERATOR', '', 'literal_operator'),
    @('CONCEPT', '', 'concept'),
    @('GLOBAL_FUNCTION', '', 'function'),
    @('MEMBER_FUNCTION', 'Owner', 'method'),
    @('CONSTRUCTOR', 'Owner', 'constructor'),
    @('DESTRUCTOR', 'Owner', 'destructor'),
    @('CLASS', '', 'class'),
    @('STRUCT', '', 'struct'),
    @('ENUM', '', 'enum'),
    @('TYPE_ALIAS', '', 'alias'),
    @('TEMPLATE_PARAMETER', '', 'parameter')
)) {
    $kindProxy.ElementType = $elementKindsType.GetField($case[0]).GetValue($null)
    $actualKind = $normalizeKindMethod.Invoke($null, [object[]]@($kindElement, $case[1]))
    if ($actualKind -cne $case[2]) {
        throw "Kind $($case[0]) must normalize to $($case[2]), got $actualKind."
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

# Exercise the production selector through the locked SDK's real virtual-slot walker.
# These already-computed inert relationships do not test parsing or C++ inheritance construction.
$overriddenMethod = $engineType.GetMethod('GetDirectOverriddenEntities', $staticFlags)
$inheritanceType = $overriddenMethod.GetParameters()[1].ParameterType
$classField = $inheritanceType.GetField('Class')
$entityField = $inheritanceType.GetField('Entity')
$slotsField = $inheritanceType.GetField('myVirtualSlots', $instanceFlags)
$hidesField = $inheritanceType.GetField('myHides', $instanceFlags)
$primarySlotType = $cppPsi.GetType('JetBrains.ReSharper.Psi.Cpp.Symbols.CppVTableEntryInfoPrimary', $true)
$inheritanceClass = [Runtime.CompilerServices.RuntimeHelpers]::GetUninitializedObject($classField.FieldType)
# Mark only the in-memory fixture cache ready; never enter SDK project/cache services.
$classField.FieldType.GetField('myIsInheritanceReady', $instanceFlags).SetValue($inheritanceClass, $true)
$completeField = $classField.FieldType.GetField('myIsComplete', $instanceFlags)
$completeField.SetValue($inheritanceClass, $true)
$derivedMethod = [Runtime.CompilerServices.RuntimeHelpers]::GetUninitializedObject($inheritanceType)
$baseMethod = [Runtime.CompilerServices.RuntimeHelpers]::GetUninitializedObject($inheritanceType)
$grandMethod = [Runtime.CompilerServices.RuntimeHelpers]::GetUninitializedObject($inheritanceType)
$hiddenMethod = [Runtime.CompilerServices.RuntimeHelpers]::GetUninitializedObject($inheritanceType)
$classField.SetValue($derivedMethod, $inheritanceClass)
# The existing proxy throws for unexpected calls. The selector must only carry these identities.
$baseEntity = [Reflection.DispatchProxy]::Create($entityField.FieldType, [ConsumerDiagnosticsDeclaredElementProxy])
$grandEntity = [Reflection.DispatchProxy]::Create($entityField.FieldType, [ConsumerDiagnosticsDeclaredElementProxy])
$hiddenEntity = [Reflection.DispatchProxy]::Create($entityField.FieldType, [ConsumerDiagnosticsDeclaredElementProxy])
$entityField.SetValue($baseMethod, $baseEntity)
$entityField.SetValue($grandMethod, $grandEntity)
$entityField.SetValue($hiddenMethod, $hiddenEntity)
$hidesField.SetValue($derivedMethod, [Activator]::CreateInstance($hidesField.FieldType, [object[]]@($hiddenMethod)))

function Assert-DirectOverriddenEntities {
    param([string] $Case, $Model, [object[]] $Expected, [bool] $Complete, [int] $Unavailable)
    $arguments = [object[]]@($eternal, $Model, $false, 0)
    $actual = $overriddenMethod.Invoke($null, $arguments)
    if ($actual.Length -ne $Expected.Length -or $arguments[2] -ne $Complete -or $arguments[3] -ne $Unavailable) {
        throw "Direct override case '$Case' returned incorrect identities or completeness."
    }
    for ($index = 0; $index -lt $Expected.Length; $index++) {
        if (-not [object]::ReferenceEquals($actual[$index], $Expected[$index])) {
            throw "Direct override case '$Case' returned a hidden or non-nearest member."
        }
    }
}

Assert-DirectOverriddenEntities -Case 'missing model' -Model $null -Expected @() -Complete $false -Unavailable 0
Assert-DirectOverriddenEntities -Case 'nonvirtual hides only' -Model $derivedMethod -Expected @() -Complete $true -Unavailable 0
# Establish that this fixture really contains a hide, which the old broader API would return.
if ($derivedMethod.GetBases($true, $true).Count -ne 1) {
    throw 'The hide-only fixture must contain a result in the SDK broader base-member query.'
}

$grandSlot = [Activator]::CreateInstance($primarySlotType, [object[]]@($grandMethod, $grandMethod, $null, $false))
$baseSlot = [Activator]::CreateInstance($primarySlotType, [object[]]@($baseMethod, $baseMethod, $grandSlot, $false))
$derivedSlot = [Activator]::CreateInstance($primarySlotType, [object[]]@($derivedMethod, $derivedMethod, $baseSlot, $false))
$slotsField.SetValue($derivedMethod, [Activator]::CreateInstance($slotsField.FieldType, [object[]]@($derivedSlot)))
Assert-DirectOverriddenEntities -Case 'nearest virtual plus unrelated hide' -Model $derivedMethod -Expected @($baseEntity) -Complete $true -Unavailable 0

$completeField.SetValue($inheritanceClass, $false)
Assert-DirectOverriddenEntities -Case 'incomplete model retains known virtual' -Model $derivedMethod -Expected @($baseEntity) -Complete $false -Unavailable 0
$completeField.SetValue($inheritanceClass, $true)
$entityField.SetValue($baseMethod, $null)
Assert-DirectOverriddenEntities -Case 'unreadable virtual entity' -Model $derivedMethod -Expected @() -Complete $true -Unavailable 1

'PASS: asset/unsupported messages, relation certainty, normalized symbol kinds, missing C++ result accounting, qualifier child traversal, and direct virtual-base selection.'
'These isolated checks do not replace live Rider semantic validation.'
