# Copyright (C) 2026 Bathur.
# Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
# See LICENSE and LICENSING.md in the public source root.

param(
    [Parameter(Mandatory = $true)][string] $RiderHome,
    [Parameter(Mandatory = $true)][string] $BackendPath
)

$ErrorActionPreference = 'Stop'
$searchTestAssemblies = [IO.Path]::Combine($RiderHome, 'lib', 'ReSharperHost')
[AppDomain]::CurrentDomain.add_AssemblyResolve({
    param($sender, $eventArgs)
    # Avoid cmdlet/module autoload while the CLR is resolving SDK dependencies.
    $dependencyFile = [IO.Path]::Combine($searchTestAssemblies, ([Reflection.AssemblyName]::new($eventArgs.Name)).Name + '.dll')
    if ([IO.File]::Exists($dependencyFile)) { return [Reflection.Assembly]::LoadFrom($dependencyFile) }
    return $null
})

$backend = [Reflection.Assembly]::LoadFrom([IO.Path]::GetFullPath($BackendPath))
$cppPsi = [Reflection.Assembly]::LoadFrom([IO.Path]::Combine($searchTestAssemblies, 'JetBrains.ReSharper.Cpp.dll'))
$queryType = $backend.GetType('Bathur.ReSharperMcpToolset.CppSearchNameQuery', $true)
$staticFlags = [Reflection.BindingFlags]'Public,NonPublic,Static'
$instanceFlags = [Reflection.BindingFlags]'Public,NonPublic,Instance'
$fromParsed = $queryType.GetMethod('FromParsedName', $staticFlags)
$matchesMethod = $queryType.GetMethod('Matches', $instanceFlags)
$compatibility = $queryType.GetMethod('TryCreateCompatibility', $staticFlags)
$tryCreate = $queryType.GetMethod('TryCreate', $staticFlags)
$namePartType = $cppPsi.GetType('JetBrains.ReSharper.Psi.Cpp.Symbols.CppQualifiedNamePart', $true)
$nameType = $cppPsi.GetType('JetBrains.ReSharper.Psi.Cpp.Symbols.CppQualifiedName', $true)
$qualType = $cppPsi.GetType('JetBrains.ReSharper.Psi.Cpp.Types.CppQualType', $true)

function New-SdkValue {
    param([string] $TypeName, [object[]] $Arguments)
    $type = $cppPsi.GetType('JetBrains.ReSharper.Psi.Cpp.' + $TypeName, $true)
    $rawArguments = [object[]]::new($Arguments.Length)
    for ($index = 0; $index -lt $Arguments.Length; $index++) {
        # Assign directly: returning an array through an if-expression pipeline would
        # enumerate it and erase the SDK constructor's typed-array argument.
        if ($null -ne $Arguments[$index]) {
            $rawArguments[$index] = $Arguments[$index].PSObject.BaseObject
        }
    }
    return ,([Activator]::CreateInstance($type, $instanceFlags, $null, $rawArguments, [Globalization.CultureInfo]::InvariantCulture))
}

function ConvertTo-SdkNamePart {
    param($Value)
    $raw = $Value.PSObject.BaseObject
    $conversion = $namePartType.GetMethod('op_Implicit', [type[]]@($raw.GetType()))
    if ($null -eq $conversion) { throw "No SDK name-part conversion for $($raw.GetType().FullName)." }
    return ,($conversion.Invoke($null, [object[]]@($raw)))
}

function New-IdentifierPart {
    param([string] $Text)
    return ,(ConvertTo-SdkNamePart (New-SdkValue 'Symbols.CppQualifiedId' @($Text)))
}

function New-SdkName {
    param([object[]] $Parts)
    if ($Parts.Length -eq 0) { throw 'A name fixture needs at least one part.' }
    $name = $nameType.GetMethod('Create', [type[]]@($namePartType)).Invoke($null, [object[]]@($Parts[0].PSObject.BaseObject))
    $append = $nameType.GetMethod('Create', [type[]]@($nameType, $namePartType))
    for ($index = 1; $index -lt $Parts.Length; $index++) {
        $name = $append.Invoke($null, [object[]]@($name.PSObject.BaseObject, $Parts[$index].PSObject.BaseObject))
    }
    return ,$name
}

function New-NamedQualType {
    param($Name)
    $emptyType = $qualType.GetMethod('EmptyType', $staticFlags).Invoke($null, @())
    return ,(New-SdkValue 'Types.CppQualType' @((New-SdkValue 'Types.CppNamedType' @($Name, $emptyType))))
}

function New-TemplatePart {
    param([string] $Name, $ArgumentType)
    $argument = New-SdkValue 'Symbols.CppTypeTemplateArgument' @($ArgumentType)
    $arguments = [Array]::CreateInstance($cppPsi.GetType('JetBrains.ReSharper.Psi.Cpp.Symbols.ICppTemplateArgument', $true), 1)
    $arguments.SetValue($argument.PSObject.BaseObject, 0)
    return ,(ConvertTo-SdkNamePart (New-SdkValue 'Symbols.CppTemplateId' @((New-IdentifierPart $Name), $null, $arguments)))
}

function New-Query {
    param([string] $InputName, $SdkName, [string[]] $ExpectedKeys)
    $query = $fromParsed.Invoke($null, [object[]]@($InputName, $SdkName.PSObject.BaseObject))
    if ($null -eq $query -or $query.IndexKey -cne $ExpectedKeys[0] -or $query.IndexKeys.Length -ne $ExpectedKeys.Length) {
        throw "Incorrect query or index-key count for '$InputName'."
    }
    for ($index = 0; $index -lt $ExpectedKeys.Length; $index++) {
        if ($query.IndexKeys[$index] -cne $ExpectedKeys[$index]) {
            throw "Incorrect SDK index key for '$InputName': '$($query.IndexKeys[$index])'."
        }
    }
    return ,$query
}

function Assert-Match {
    param([string] $Case, $Query, [string] $ShortName, [string] $QualifiedName, [bool] $Expected)
    $actual = $matchesMethod.Invoke($Query.PSObject.BaseObject, [object[]]@($ShortName, $QualifiedName))
    if ($actual -ne $Expected) { throw "Exact-name matching failed: $Case." }
}

# Construct real SDK name values. No solution/cache, parsed PSI or symbol index is fabricated.
$n = New-IdentifierPart 'N'
$thing = New-IdentifierPart 'Thing'
$plain = New-Query 'Thing' (New-SdkName @($thing)) @('Thing', 'operator ""Thing')
if ($plain.RequiresQualifiedName) { throw 'Ordinary short names must not request extra qualified-name work.' }
Assert-Match 'ordinary short name' $plain 'Thing' 'N::Thing' $true
Assert-Match 'ordinary short-name case' $plain 'thing' 'N::thing' $false
Assert-Match 'ordinary prefix is not a match' $plain 'ThingMore' 'N::ThingMore' $false

$qualified = New-Query 'N::Thing' (New-SdkName @($n, $thing)) @('Thing')
$globalQualified = New-Query '::N::Thing' (New-SdkName @($n, $thing)) @('Thing')
if (-not $qualified.RequiresQualifiedName) { throw 'Qualified queries must retain the owner constraint.' }
foreach ($query in @($qualified, $globalQualified)) {
    Assert-Match 'qualified name' $query 'Thing' 'N::Thing' $true
    Assert-Match 'wrong namespace' $query 'Thing' 'Other::Thing' $false
    Assert-Match 'qualified-name case' $query 'Thing' 'n::Thing' $false
}

# Ordinary names take the production fast path with no C++ module or PSI fragment.
# Compare it with the same production model built from real SDK name values.
$unicodeShort = New-IdentifierPart '_名称2'
$unicodeOwner = New-IdentifierPart '命名空间'
foreach ($case in @(
    @{ Input = 'Thing'; Name = (New-SdkName @($thing)); Keys = @('Thing', 'operator ""Thing'); Short = 'Thing'; Qualified = 'N::Thing' },
    @{ Input = 'N::Thing'; Name = (New-SdkName @($n, $thing)); Keys = @('Thing'); Short = 'Thing'; Qualified = 'N::Thing' },
    @{ Input = '::N::Thing'; Name = (New-SdkName @($n, $thing)); Keys = @('Thing'); Short = 'Thing'; Qualified = 'N::Thing' },
    @{ Input = ' _名称2 '; Name = (New-SdkName @($unicodeShort)); Keys = @('_名称2', 'operator ""_名称2'); Short = '_名称2'; Qualified = '命名空间::_名称2' },
    @{ Input = '::命名空间::_名称2'; Name = (New-SdkName @($unicodeOwner, $unicodeShort)); Keys = @('_名称2'); Short = '_名称2'; Qualified = '命名空间::_名称2' }
)) {
    $referenceQuery = New-Query $case.Input $case.Name $case.Keys
    $compatibilityArguments = [object[]]@($case.Input, $null)
    if (-not $compatibility.Invoke($null, $compatibilityArguments)) { throw "Ordinary fast path rejected '$($case.Input)'." }
    $fullArguments = [object[]]@($null, $case.Input, $null, $null)
    if (-not $tryCreate.Invoke($null, $fullArguments) -or $fullArguments[3]) {
        throw "Ordinary query '$($case.Input)' unexpectedly requires a C++ module."
    }
    foreach ($fastQuery in @($compatibilityArguments[1], $fullArguments[2])) {
        if ($fastQuery.IndexKeys.Length -ne $referenceQuery.IndexKeys.Length -or
            $fastQuery.RequiresQualifiedName -ne $referenceQuery.RequiresQualifiedName) {
            throw 'Ordinary fast path changed the SDK model index-key count or matching scope.'
        }
        for ($index = 0; $index -lt $referenceQuery.IndexKeys.Length; $index++) {
            if ($fastQuery.IndexKeys[$index] -cne $referenceQuery.IndexKeys[$index]) {
                throw 'Ordinary fast path changed an SDK-derived index key.'
            }
        }
        Assert-Match 'ordinary fast-path positive match' $fastQuery $case.Short $case.Qualified $true
        foreach ($candidate in @(
            @($case.Short, $case.Qualified),
            @($case.Short, 'Other::' + $case.Short),
            @($case.Short + 'Extra', $case.Qualified + 'Extra'),
            @($case.Short, $null)
        )) {
            $expected = $matchesMethod.Invoke($referenceQuery.PSObject.BaseObject, [object[]]@($candidate[0], $candidate[1]))
            Assert-Match 'ordinary fast-path parity with SDK name model' $fastQuery $candidate[0] $candidate[1] $expected
        }
    }
}
foreach ($inputName in @('', '::', 'N::', '::N::', 'N::::Thing', 'N:Thing', '1Name', 'N::1Name', 'N:: Thing', 'Name with space', 'N::Thing;')) {
    $arguments = [object[]]@($inputName, $null)
    if ($compatibility.Invoke($null, $arguments) -or $null -ne $arguments[1]) {
        throw "Malformed identifier chain '$inputName' entered the ordinary fast path."
    }
}
foreach ($inputName in @('Box<int>', 'Box<conversion>', 'N::operator<<', 'operator bool', 'N::""_audit')) {
    $arguments = [object[]]@($inputName, $null)
    if ($compatibility.Invoke($null, $arguments)) { throw 'Special C++ syntax must continue to the SDK parser.' }
}

$typeArgument = New-NamedQualType (New-SdkName @((New-IdentifierPart 'Arg'), (New-IdentifierPart 'Type')))
$box = New-TemplatePart 'Box' $typeArgument
$boxName = New-SdkName @($box)
$qualifiedBoxName = New-SdkName @($n, $box)
$boxDisplay = $boxName.ToString()
$qualifiedBoxDisplay = $qualifiedBoxName.ToString()
if ($boxDisplay -notmatch 'Arg::Type') { throw 'Template fixture must really contain a nested namespace qualifier.' }
$explicitBox = New-Query $boxDisplay $boxName @('Box')
$qualifiedBox = New-Query $qualifiedBoxDisplay $qualifiedBoxName @('Box')
Assert-Match 'unqualified explicit template-id' $explicitBox 'Box' $qualifiedBoxDisplay $true
Assert-Match 'template-id cannot match primary' $explicitBox 'Box' 'N::Box' $false
Assert-Match 'template argument must match exactly' $explicitBox 'Box' 'N::Box<Other::Type>' $false
Assert-Match 'qualified template-id' $qualifiedBox 'Box' $qualifiedBoxDisplay $true
Assert-Match 'template namespace must match' $qualifiedBox 'Box' ('Other::' + $boxDisplay) $false
$plainBox = New-Query 'Box' (New-SdkName @((New-IdentifierPart 'Box'))) @('Box', 'operator ""Box')
Assert-Match 'existing bare-name specialization family' $plainBox 'Box' $qualifiedBoxDisplay $true
$nestedBox = New-TemplatePart 'Outer' (New-NamedQualType $boxName)
$nestedName = New-SdkName @($n, $nestedBox)
$nestedQuery = New-Query ($nestedName.ToString()) $nestedName @('Outer')
Assert-Match 'nested template-id' $nestedQuery 'Outer' ($nestedName.ToString()) $true
Assert-Match 'nested template wrong argument' $nestedQuery 'Outer' 'N::Outer<Box<Other::Type>>' $false
$methodName = New-SdkName @($n, $box, (New-IdentifierPart 'Method'))
$methodQuery = New-Query ($methodName.ToString()) $methodName @('Method')
Assert-Match 'template owner' $methodQuery 'Method' ($methodName.ToString()) $true
Assert-Match 'template owner cannot collapse' $methodQuery 'Method' 'N::Box::Method' $false

$conversionIdentifier = New-NamedQualType (New-SdkName @((New-IdentifierPart 'conversion')))
$markerTemplatePart = New-TemplatePart 'Box' $conversionIdentifier
$markerTemplateName = New-SdkName @($markerTemplatePart)
if ($markerTemplateName.ToString() -cne 'Box<conversion>') { throw 'The marker fixture must be a real SDK template-id containing the identifier conversion.' }
$markerTemplate = New-Query 'Box<conversion>' $markerTemplateName @('Box')
Assert-Match 'conversion identifier is a template argument' $markerTemplate 'Box' 'N::Box<conversion>' $true
Assert-Match 'conversion template argument remains exact' $markerTemplate 'Box' 'N::Box<Other>' $false
# A null module tests dispatch and infrastructure-error classification before parsing.
# It must throw with the original cause, not report unsupported syntax.
$markerArguments = [object[]]@($null, 'Box<conversion>', $null, $null)
$markerFailure = $null
try {
    $null = $tryCreate.Invoke($null, $markerArguments)
}
catch {
    $markerFailure = $_.Exception
    # Remove only invocation wrappers; retain the production stage/cause chain.
    while (($markerFailure -is [Reflection.TargetInvocationException] -or
            $markerFailure -is [Management.Automation.MethodInvocationException]) -and
           $null -ne $markerFailure.InnerException) {
        $markerFailure = $markerFailure.InnerException
    }
}
if ($markerFailure -isnot [InvalidOperationException] -or
    $markerFailure.Message -cne 'Rider C++ search-name preparation failed during resolve C++ module.' -or
    $markerFailure.InnerException -isnot [InvalidOperationException] -or
    $markerFailure.InnerException.Message -cne 'The current C++ module is unavailable for name parsing.' -or
    $null -ne $markerArguments[2] -or $null -ne $markerArguments[3]) {
    throw 'Missing parsing infrastructure must preserve its stage and cause without becoming an unsupported name.'
}

$numericKinds = $cppPsi.GetType('JetBrains.ReSharper.Psi.Cpp.Types.CppNumericTypeKindUtil+AllNumericTypeKinds', $true)
$boolKind = $null
foreach ($kind in $numericKinds.GetField('Kinds', $staticFlags).GetValue($null)) {
    if ($kind.Name -ceq 'bool') { $boolKind = $kind; break }
}
if ($null -eq $boolKind) { throw 'The locked SDK bool numeric type is unavailable.' }
$boolType = New-SdkValue 'Types.CppQualType' @((New-SdkValue 'Types.CppNumericType' @($boolKind)))
$conversionPart = ConvertTo-SdkNamePart (New-SdkValue 'Symbols.CppConversionId' @($boolType))
$conversionName = New-SdkName @($n, (New-IdentifierPart 'Convert'), $conversionPart)
$conversionQuery = New-Query 'N::Convert::operator bool' $conversionName @('operator')
Assert-Match 'native conversion spelling to SDK display' $conversionQuery 'operator' ($conversionName.ToString()) $true
Assert-Match 'conversion target type differs' $conversionQuery 'operator' 'N::Convert::<conversion>NUM(int)' $false
Assert-Match 'conversion owner differs' $conversionQuery 'operator' 'Other::Convert::<conversion>NUM(bool)' $false
$qualifiedConversionPart = ConvertTo-SdkNamePart (New-SdkValue 'Symbols.CppConversionId' @($typeArgument))
$qualifiedConversionName = New-SdkName @($n, (New-IdentifierPart 'Convert'), $qualifiedConversionPart)
$qualifiedConversion = New-Query 'N::Convert::operator Arg::Type' $qualifiedConversionName @('operator')
Assert-Match 'namespace in conversion target is not owner separator' $qualifiedConversion 'operator' ($qualifiedConversionName.ToString()) $true
Assert-Match 'conversion target namespace must not disappear' $qualifiedConversion 'operator' ($conversionName.ToString()) $false

# Returned internal descriptors are opaque exact text, not a bool-only decoder.
$rewriteConversion = $queryType.GetMethod('TryRewriteReportedConversion', $staticFlags)
$markerOwnerConversionName = New-SdkName @($markerTemplatePart, $conversionPart)
$markerOwnerReported = 'Box<conversion>::<conversion>NUM(bool)'
$markerOwnerArguments = [object[]]@($markerOwnerReported, $null, $null)
if (-not $rewriteConversion.Invoke($null, $markerOwnerArguments) -or
    $markerOwnerArguments[1] -cne 'Box<conversion>::operator bool' -or $markerOwnerArguments[2]) {
    throw 'A template argument named conversion must not hide the final returned-conversion marker.'
}
if ($markerOwnerConversionName.ToString() -cne $markerOwnerReported) {
    throw 'The conversion-owner fixture must preserve the SDK template and conversion parts.'
}
$markerOwnerQuery = New-Query $markerOwnerReported $markerOwnerConversionName @('operator')
Assert-Match 'returned conversion with marker-like owner' $markerOwnerQuery 'operator' $markerOwnerReported $true
Assert-Match 'returned conversion owner template remains exact' $markerOwnerQuery 'operator' 'Box<Other>::<conversion>NUM(bool)' $false
Assert-Match 'returned conversion descriptor remains exact' $markerOwnerQuery 'operator' 'Box<conversion>::<conversion>NUM(int)' $false
$markerOwnerNative = New-Query 'Box<conversion>::operator bool' $markerOwnerConversionName @('operator')
Assert-Match 'native conversion with marker-like owner' $markerOwnerNative 'operator' $markerOwnerReported $true
foreach ($descriptor in @('NUM(bool)', 'POINTER(CLASS(Arg::Type))')) {
    $reportedName = 'N::Convert::<conversion>' + $descriptor
    $arguments = [object[]]@($reportedName, $null, $null)
    if (-not $rewriteConversion.Invoke($null, $arguments) -or $arguments[1] -cne 'N::Convert::operator bool' -or $arguments[2]) {
        throw 'Returned conversion rewriting must only supply a neutral terminal for real SDK owner validation.'
    }
    $reportedQuery = New-Query $reportedName $conversionName @('operator')
    Assert-Match 'opaque conversion descriptor preserved' $reportedQuery 'operator' $reportedName $true
    Assert-Match 'opaque conversion descriptor exactness' $reportedQuery 'operator' ($reportedName + 'Extra') $false
    Assert-Match 'opaque conversion owner exactness' $reportedQuery 'operator' ('Other::Convert::<conversion>' + $descriptor) $false
}
foreach ($invalid in @('N::<conversion>', 'N::<conversion>NUM(bool);', "N::<conversion>NUM(`nbool)", 'N::<conversion><conversion>NUM(bool)')) {
    $arguments = [object[]]@($invalid, $null, $null)
    $rewritten = $rewriteConversion.Invoke($null, $arguments)
    if ($rewritten -or [string]::IsNullOrWhiteSpace($arguments[2])) {
        throw 'Malformed conversion compatibility input must be rejected with an explanation.'
    }
    # Unlike a missing module, explicit malformed-name rejection must remain a
    # normal false/error result and must not reach the SDK-dependent path.
    $creationArguments = [object[]]@($null, $invalid, $null, $null)
    if ($tryCreate.Invoke($null, $creationArguments) -or $null -ne $creationArguments[2] -or
        [string]::IsNullOrWhiteSpace($creationArguments[3])) {
        throw 'TryCreate must reject malformed conversion input before requesting parsing infrastructure.'
    }
}

$literalPart = ConvertTo-SdkNamePart (New-SdkValue 'Symbols.CppUserDefinedLiteralId' @('_audit'))
$literalName = New-SdkName @($n, $literalPart)
$literalQuery = New-Query 'N::operator ""_audit' $literalName @('operator ""_audit')
Assert-Match 'native literal name' $literalQuery '_audit' 'N::""_audit' $true
Assert-Match 'literal suffix exactness' $literalQuery '_auditExtra' 'N::""_auditExtra' $false
Assert-Match 'literal owner exactness' $literalQuery '_audit' 'Other::""_audit' $false
$literalShort = New-Query '_audit' (New-SdkName @((New-IdentifierPart '_audit'))) @('_audit', 'operator ""_audit')
Assert-Match 'returned literal short name' $literalShort '_audit' 'N::""_audit' $true
Assert-Match 'same-named ordinary symbol remains eligible' $literalShort '_audit' 'N::_audit' $true
$rewriteLiteral = $queryType.GetMethod('TryRewriteReportedLiteral', $staticFlags)
$literalArguments = [object[]]@('N::""_audit', $null)
if (-not $rewriteLiteral.Invoke($null, $literalArguments) -or $literalArguments[1] -cne 'N::operator ""_audit') {
    throw 'Returned literal QName must rewrite to valid source spelling before SDK parsing.'
}
$reportedLiteral = New-Query 'N::""_audit' $literalName @('operator ""_audit')
Assert-Match 'returned literal QName' $reportedLiteral '_audit' 'N::""_audit' $true

foreach ($inputName in @('operator', '::operator', ' operator ')) {
    $arguments = [object[]]@($inputName, $null)
    if (-not $compatibility.Invoke($null, $arguments)) { throw 'The existing operator short-name family was rejected.' }
    $family = $arguments[1]
    if ($family.IndexKeys.Length -ne 1 -or $family.IndexKey -cne 'operator' -or $family.RequiresQualifiedName) {
        throw 'Bare operator compatibility must preserve its single index key and short-name matching.'
    }
    Assert-Match 'operator conversion family' $family 'operator' 'N::Convert::<conversion>NUM(bool)' $true
    Assert-Match 'operator family excludes operator punctuation' $family 'operator+' 'N::Convert::operator+' $false
}

$operatorKind = $cppPsi.GetType('JetBrains.ReSharper.Psi.Cpp.Symbols.CppOperatorKind', $true)
foreach ($case in @(@('LTLT', 'operator<<'), @('PAREN', 'operator()'), @('SUBSCRIPT', 'operator[]'), @('LTEQGT', 'operator<=>'))) {
    $operator = New-SdkValue 'Symbols.CppOperatorId' @($operatorKind.GetField($case[0]).GetValue($null))
    $operatorPart = ConvertTo-SdkNamePart $operator
    $operatorName = New-SdkName @($n, (New-IdentifierPart 'Convert'), $operatorPart)
    $operatorQuery = New-Query ('N::Convert::' + $case[1]) $operatorName @($case[1])
    Assert-Match 'operator punctuation remains a single name part' $operatorQuery $case[1] ($operatorName.ToString()) $true
    Assert-Match 'operator owner exactness' $operatorQuery $case[1] ('Other::Convert::' + $case[1]) $false
    $bareOperator = New-Query $case[1] (New-SdkName @($operatorPart)) @($case[1])
    if ($bareOperator.RequiresQualifiedName) { throw 'An ordinary operator short name must not request qualified-name work.' }
    Assert-Match 'operator short name retains ordinary behavior' $bareOperator $case[1] ($operatorName.ToString()) $true
}

'PASS: production ordinary-name fast path, SDK-name mapping, exact matching, template identities, conversion/literal compatibility, bounded index keys, and syntax/infrastructure failure classification.'
'Ordinary fast paths run without a C++ module; special-name tests construct SDK name values. No fragment parsing, PSI resolution, index queries, or live Rider validation is performed.'
