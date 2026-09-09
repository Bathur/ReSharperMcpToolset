// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

@file:Suppress("EXPERIMENTAL_API_USAGE","EXPERIMENTAL_UNSIGNED_LITERALS","PackageDirectoryMismatch","UnusedImport","unused","LocalVariableName","CanBeVal","PropertyName","EnumEntryName","ClassName","ObjectPropertyName","UnnecessaryVariable","SpellCheckingInspection")
package local.bathur.resharper.mcp.toolset

import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.base.*
import com.jetbrains.rd.framework.impl.*

import com.jetbrains.rd.util.lifetime.*
import com.jetbrains.rd.util.reactive.*
import com.jetbrains.rd.util.string.*
import com.jetbrains.rd.util.*
import kotlin.time.Duration
import kotlin.reflect.KClass
import kotlin.jvm.JvmStatic



/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:13]
 */
class BathurReSharperMcpToolsetModel private constructor(
    private val _inspectSymbol: RdCall<CppTargetRequest, InspectSymbolResponse>,
    private val _searchSymbols: RdCall<SearchSymbolsRequest, SearchSymbolsResponse>,
    private val _listSymbolsInFile: RdCall<CppFileOutlineRequest, ListSymbolsInFileResponse>,
    private val _getCppFileSemanticState: RdCall<CppFileSemanticStateRequest, CppFileSemanticStateResponse>,
    private val _getDiagnostics: RdCall<CppDiagnosticsRequest, GetDiagnosticsResponse>,
    private val _findReferences: RdCall<CppPagedTargetRequest, FindReferencesResponse>,
    private val _getDirectBaseTypes: RdCall<CppPagedTargetRequest, BaseTypesResponse>,
    private val _findDerivedTypes: RdCall<CppPagedTargetRequest, RelatedSymbolsResponse>,
    private val _getDirectOverriddenMembers: RdCall<CppPagedTargetRequest, RelatedSymbolsResponse>,
    private val _findOverridingMembers: RdCall<CppPagedTargetRequest, RelatedSymbolsResponse>
) : RdExtBase() {
    //companion
    
    companion object : ISerializersOwner {
        
        override fun registerSerializersCore(serializers: ISerializers)  {
            val classLoader = javaClass.classLoader
            serializers.register(LazyCompanionMarshaller(RdId(-330948089425930476), classLoader, "local.bathur.resharper.mcp.toolset.CppSourcePosition"))
            serializers.register(LazyCompanionMarshaller(RdId(-5271211828164851011), classLoader, "local.bathur.resharper.mcp.toolset.CppSemanticDiagnostic"))
            serializers.register(LazyCompanionMarshaller(RdId(540735207878941837), classLoader, "local.bathur.resharper.mcp.toolset.CppPageInfo"))
            serializers.register(LazyCompanionMarshaller(RdId(8567227040194444350), classLoader, "local.bathur.resharper.mcp.toolset.CppSymbolSummary"))
            serializers.register(LazyCompanionMarshaller(RdId(-5682256638303559045), classLoader, "local.bathur.resharper.mcp.toolset.CppInspectedSymbol"))
            serializers.register(LazyCompanionMarshaller(RdId(8749308407863890190), classLoader, "local.bathur.resharper.mcp.toolset.CppTargetRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(5998817664576190137), classLoader, "local.bathur.resharper.mcp.toolset.CppPagedTargetRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(8998747142779480239), classLoader, "local.bathur.resharper.mcp.toolset.SearchSymbolsRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(-1321815825372745415), classLoader, "local.bathur.resharper.mcp.toolset.CppFileOutlineRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(-4751034183951864336), classLoader, "local.bathur.resharper.mcp.toolset.CppFileSemanticStateRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(-7099897141130798093), classLoader, "local.bathur.resharper.mcp.toolset.CppDiagnosticsRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(-8940797202119366504), classLoader, "local.bathur.resharper.mcp.toolset.CppReferenceResult"))
            serializers.register(LazyCompanionMarshaller(RdId(-2243251211166745000), classLoader, "local.bathur.resharper.mcp.toolset.CppBaseTypeResult"))
            serializers.register(LazyCompanionMarshaller(RdId(-1907257425276055312), classLoader, "local.bathur.resharper.mcp.toolset.CppRelatedSymbolResult"))
            serializers.register(LazyCompanionMarshaller(RdId(-3544344833785145387), classLoader, "local.bathur.resharper.mcp.toolset.CppFileSymbolOccurrence"))
            serializers.register(LazyCompanionMarshaller(RdId(-2668147198038132653), classLoader, "local.bathur.resharper.mcp.toolset.CppDiagnosticsSourceFile"))
            serializers.register(LazyCompanionMarshaller(RdId(2267910565068732198), classLoader, "local.bathur.resharper.mcp.toolset.CppDiagnosticRange"))
            serializers.register(LazyCompanionMarshaller(RdId(2746252322903458610), classLoader, "local.bathur.resharper.mcp.toolset.CppDiagnosticFinding"))
            serializers.register(LazyCompanionMarshaller(RdId(2746252361769873568), classLoader, "local.bathur.resharper.mcp.toolset.CppDiagnosticsDaemon"))
            serializers.register(LazyCompanionMarshaller(RdId(1264117371964177995), classLoader, "local.bathur.resharper.mcp.toolset.CppDiagnosticsMetadata"))
            serializers.register(LazyCompanionMarshaller(RdId(-2301226452322223398), classLoader, "local.bathur.resharper.mcp.toolset.InspectSymbolResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(2260000320573547041), classLoader, "local.bathur.resharper.mcp.toolset.SearchSymbolsResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(-4750332656891773835), classLoader, "local.bathur.resharper.mcp.toolset.FindReferencesResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(4803068778835465398), classLoader, "local.bathur.resharper.mcp.toolset.BaseTypesResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(-6339707347220064380), classLoader, "local.bathur.resharper.mcp.toolset.RelatedSymbolsResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(7030377938158652300), classLoader, "local.bathur.resharper.mcp.toolset.ListSymbolsInFileResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(291892887221552384), classLoader, "local.bathur.resharper.mcp.toolset.CppFileSemanticStateResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(7451040521766080202), classLoader, "local.bathur.resharper.mcp.toolset.GetDiagnosticsResponse"))
        }
        
        
        
        
        
        const val serializationHash = 6438432183570123472L
        
    }
    override val serializersOwner: ISerializersOwner get() = BathurReSharperMcpToolsetModel
    override val serializationHash: Long get() = BathurReSharperMcpToolsetModel.serializationHash
    
    //fields
    val inspectSymbol: IRdCall<CppTargetRequest, InspectSymbolResponse> get() = _inspectSymbol
    val searchSymbols: IRdCall<SearchSymbolsRequest, SearchSymbolsResponse> get() = _searchSymbols
    val listSymbolsInFile: IRdCall<CppFileOutlineRequest, ListSymbolsInFileResponse> get() = _listSymbolsInFile
    val getCppFileSemanticState: IRdCall<CppFileSemanticStateRequest, CppFileSemanticStateResponse> get() = _getCppFileSemanticState
    val getDiagnostics: IRdCall<CppDiagnosticsRequest, GetDiagnosticsResponse> get() = _getDiagnostics
    val findReferences: IRdCall<CppPagedTargetRequest, FindReferencesResponse> get() = _findReferences
    val getDirectBaseTypes: IRdCall<CppPagedTargetRequest, BaseTypesResponse> get() = _getDirectBaseTypes
    val findDerivedTypes: IRdCall<CppPagedTargetRequest, RelatedSymbolsResponse> get() = _findDerivedTypes
    val getDirectOverriddenMembers: IRdCall<CppPagedTargetRequest, RelatedSymbolsResponse> get() = _getDirectOverriddenMembers
    val findOverridingMembers: IRdCall<CppPagedTargetRequest, RelatedSymbolsResponse> get() = _findOverridingMembers
    //methods
    //initializer
    init {
        _inspectSymbol.async = true
        _searchSymbols.async = true
        _listSymbolsInFile.async = true
        _getCppFileSemanticState.async = true
        _getDiagnostics.async = true
        _findReferences.async = true
        _getDirectBaseTypes.async = true
        _findDerivedTypes.async = true
        _getDirectOverriddenMembers.async = true
        _findOverridingMembers.async = true
    }
    
    init {
        bindableChildren.add("inspectSymbol" to _inspectSymbol)
        bindableChildren.add("searchSymbols" to _searchSymbols)
        bindableChildren.add("listSymbolsInFile" to _listSymbolsInFile)
        bindableChildren.add("getCppFileSemanticState" to _getCppFileSemanticState)
        bindableChildren.add("getDiagnostics" to _getDiagnostics)
        bindableChildren.add("findReferences" to _findReferences)
        bindableChildren.add("getDirectBaseTypes" to _getDirectBaseTypes)
        bindableChildren.add("findDerivedTypes" to _findDerivedTypes)
        bindableChildren.add("getDirectOverriddenMembers" to _getDirectOverriddenMembers)
        bindableChildren.add("findOverridingMembers" to _findOverridingMembers)
    }
    
    //secondary constructor
    internal constructor(
    ) : this(
        RdCall<CppTargetRequest, InspectSymbolResponse>(CppTargetRequest, InspectSymbolResponse),
        RdCall<SearchSymbolsRequest, SearchSymbolsResponse>(SearchSymbolsRequest, SearchSymbolsResponse),
        RdCall<CppFileOutlineRequest, ListSymbolsInFileResponse>(CppFileOutlineRequest, ListSymbolsInFileResponse),
        RdCall<CppFileSemanticStateRequest, CppFileSemanticStateResponse>(CppFileSemanticStateRequest, CppFileSemanticStateResponse),
        RdCall<CppDiagnosticsRequest, GetDiagnosticsResponse>(CppDiagnosticsRequest, GetDiagnosticsResponse),
        RdCall<CppPagedTargetRequest, FindReferencesResponse>(CppPagedTargetRequest, FindReferencesResponse),
        RdCall<CppPagedTargetRequest, BaseTypesResponse>(CppPagedTargetRequest, BaseTypesResponse),
        RdCall<CppPagedTargetRequest, RelatedSymbolsResponse>(CppPagedTargetRequest, RelatedSymbolsResponse),
        RdCall<CppPagedTargetRequest, RelatedSymbolsResponse>(CppPagedTargetRequest, RelatedSymbolsResponse),
        RdCall<CppPagedTargetRequest, RelatedSymbolsResponse>(CppPagedTargetRequest, RelatedSymbolsResponse)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("BathurReSharperMcpToolsetModel (")
        printer.indent {
            print("inspectSymbol = "); _inspectSymbol.print(printer); println()
            print("searchSymbols = "); _searchSymbols.print(printer); println()
            print("listSymbolsInFile = "); _listSymbolsInFile.print(printer); println()
            print("getCppFileSemanticState = "); _getCppFileSemanticState.print(printer); println()
            print("getDiagnostics = "); _getDiagnostics.print(printer); println()
            print("findReferences = "); _findReferences.print(printer); println()
            print("getDirectBaseTypes = "); _getDirectBaseTypes.print(printer); println()
            print("findDerivedTypes = "); _findDerivedTypes.print(printer); println()
            print("getDirectOverriddenMembers = "); _getDirectOverriddenMembers.print(printer); println()
            print("findOverridingMembers = "); _findOverridingMembers.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): BathurReSharperMcpToolsetModel   {
        return BathurReSharperMcpToolsetModel(
            _inspectSymbol.deepClonePolymorphic(),
            _searchSymbols.deepClonePolymorphic(),
            _listSymbolsInFile.deepClonePolymorphic(),
            _getCppFileSemanticState.deepClonePolymorphic(),
            _getDiagnostics.deepClonePolymorphic(),
            _findReferences.deepClonePolymorphic(),
            _getDirectBaseTypes.deepClonePolymorphic(),
            _findDerivedTypes.deepClonePolymorphic(),
            _getDirectOverriddenMembers.deepClonePolymorphic(),
            _findOverridingMembers.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
    override val extThreading: ExtThreadingKind get() = ExtThreadingKind.Default
}
val com.jetbrains.rd.ide.model.Solution.bathurReSharperMcpToolsetModel get() = getOrCreateExtension("bathurReSharperMcpToolsetModel", ::BathurReSharperMcpToolsetModel)



/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:207]
 */
data class BaseTypesResponse (
    val status: String,
    val targets: Array<CppSymbolSummary>,
    val baseTypes: Array<CppBaseTypeResult>,
    val page: CppPageInfo,
    val searchScope: String,
    val diagnostics: Array<CppSemanticDiagnostic>
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(status)
        buffer.writeArray(targets) { CppSymbolSummary.write(ctx, buffer, it) }
        buffer.writeArray(baseTypes) { CppBaseTypeResult.write(ctx, buffer, it) }
        CppPageInfo.write(ctx, buffer, page)
        buffer.writeString(searchScope)
        buffer.writeArray(diagnostics) { CppSemanticDiagnostic.write(ctx, buffer, it) }
    }
    //companion
    
    companion object : IMarshaller<BaseTypesResponse> {
        override val _type: KClass<BaseTypesResponse> = BaseTypesResponse::class
        override val id: RdId get() = RdId(4803068778835465398)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): BaseTypesResponse  {
            val status = buffer.readString()
            val targets = buffer.readArray {CppSymbolSummary.read(ctx, buffer)}
            val baseTypes = buffer.readArray {CppBaseTypeResult.read(ctx, buffer)}
            val page = CppPageInfo.read(ctx, buffer)
            val searchScope = buffer.readString()
            val diagnostics = buffer.readArray {CppSemanticDiagnostic.read(ctx, buffer)}
            return BaseTypesResponse(status, targets, baseTypes, page, searchScope, diagnostics)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: BaseTypesResponse)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as BaseTypesResponse
        
        if (status != other.status) return false
        if (!(targets contentDeepEquals other.targets)) return false
        if (!(baseTypes contentDeepEquals other.baseTypes)) return false
        if (page != other.page) return false
        if (searchScope != other.searchScope) return false
        if (!(diagnostics contentDeepEquals other.diagnostics)) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + status.hashCode()
        __r = __r*31 + targets.contentDeepHashCode()
        __r = __r*31 + baseTypes.contentDeepHashCode()
        __r = __r*31 + page.hashCode()
        __r = __r*31 + searchScope.hashCode()
        __r = __r*31 + diagnostics.contentDeepHashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("BaseTypesResponse (")
        printer.indent {
            print("status = "); status.print(printer); println()
            print("targets = "); targets.print(printer); println()
            print("baseTypes = "); baseTypes.print(printer); println()
            print("page = "); page.print(printer); println()
            print("searchScope = "); searchScope.print(printer); println()
            print("diagnostics = "); diagnostics.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:110]
 */
data class CppBaseTypeResult (
    val symbol: CppSymbolSummary,
    val accessibility: String,
    val isVirtual: String,
    val isImplicit: String,
    val isPackExpansion: String
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        CppSymbolSummary.write(ctx, buffer, symbol)
        buffer.writeString(accessibility)
        buffer.writeString(isVirtual)
        buffer.writeString(isImplicit)
        buffer.writeString(isPackExpansion)
    }
    //companion
    
    companion object : IMarshaller<CppBaseTypeResult> {
        override val _type: KClass<CppBaseTypeResult> = CppBaseTypeResult::class
        override val id: RdId get() = RdId(-2243251211166745000)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppBaseTypeResult  {
            val symbol = CppSymbolSummary.read(ctx, buffer)
            val accessibility = buffer.readString()
            val isVirtual = buffer.readString()
            val isImplicit = buffer.readString()
            val isPackExpansion = buffer.readString()
            return CppBaseTypeResult(symbol, accessibility, isVirtual, isImplicit, isPackExpansion)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppBaseTypeResult)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppBaseTypeResult
        
        if (symbol != other.symbol) return false
        if (accessibility != other.accessibility) return false
        if (isVirtual != other.isVirtual) return false
        if (isImplicit != other.isImplicit) return false
        if (isPackExpansion != other.isPackExpansion) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + symbol.hashCode()
        __r = __r*31 + accessibility.hashCode()
        __r = __r*31 + isVirtual.hashCode()
        __r = __r*31 + isImplicit.hashCode()
        __r = __r*31 + isPackExpansion.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppBaseTypeResult (")
        printer.indent {
            print("symbol = "); symbol.print(printer); println()
            print("accessibility = "); accessibility.print(printer); println()
            print("isVirtual = "); isVirtual.print(printer); println()
            print("isImplicit = "); isImplicit.print(printer); println()
            print("isPackExpansion = "); isPackExpansion.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:151]
 */
data class CppDiagnosticFinding (
    val severity: String,
    val inspectionId: String,
    val compilerIds: Array<String>,
    val highlightingType: String,
    val message: String,
    val stage: String,
    val range: CppDiagnosticRange
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(severity)
        buffer.writeString(inspectionId)
        buffer.writeArray(compilerIds) { buffer.writeString(it) }
        buffer.writeString(highlightingType)
        buffer.writeString(message)
        buffer.writeString(stage)
        CppDiagnosticRange.write(ctx, buffer, range)
    }
    //companion
    
    companion object : IMarshaller<CppDiagnosticFinding> {
        override val _type: KClass<CppDiagnosticFinding> = CppDiagnosticFinding::class
        override val id: RdId get() = RdId(2746252322903458610)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppDiagnosticFinding  {
            val severity = buffer.readString()
            val inspectionId = buffer.readString()
            val compilerIds = buffer.readArray {buffer.readString()}
            val highlightingType = buffer.readString()
            val message = buffer.readString()
            val stage = buffer.readString()
            val range = CppDiagnosticRange.read(ctx, buffer)
            return CppDiagnosticFinding(severity, inspectionId, compilerIds, highlightingType, message, stage, range)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppDiagnosticFinding)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppDiagnosticFinding
        
        if (severity != other.severity) return false
        if (inspectionId != other.inspectionId) return false
        if (!(compilerIds contentDeepEquals other.compilerIds)) return false
        if (highlightingType != other.highlightingType) return false
        if (message != other.message) return false
        if (stage != other.stage) return false
        if (range != other.range) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + severity.hashCode()
        __r = __r*31 + inspectionId.hashCode()
        __r = __r*31 + compilerIds.contentDeepHashCode()
        __r = __r*31 + highlightingType.hashCode()
        __r = __r*31 + message.hashCode()
        __r = __r*31 + stage.hashCode()
        __r = __r*31 + range.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppDiagnosticFinding (")
        printer.indent {
            print("severity = "); severity.print(printer); println()
            print("inspectionId = "); inspectionId.print(printer); println()
            print("compilerIds = "); compilerIds.print(printer); println()
            print("highlightingType = "); highlightingType.print(printer); println()
            print("message = "); message.print(printer); println()
            print("stage = "); stage.print(printer); println()
            print("range = "); range.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:142]
 */
data class CppDiagnosticRange (
    val filePath: String,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val endExclusive: Boolean
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(filePath)
        buffer.writeInt(startLine)
        buffer.writeInt(startColumn)
        buffer.writeInt(endLine)
        buffer.writeInt(endColumn)
        buffer.writeBool(endExclusive)
    }
    //companion
    
    companion object : IMarshaller<CppDiagnosticRange> {
        override val _type: KClass<CppDiagnosticRange> = CppDiagnosticRange::class
        override val id: RdId get() = RdId(2267910565068732198)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppDiagnosticRange  {
            val filePath = buffer.readString()
            val startLine = buffer.readInt()
            val startColumn = buffer.readInt()
            val endLine = buffer.readInt()
            val endColumn = buffer.readInt()
            val endExclusive = buffer.readBool()
            return CppDiagnosticRange(filePath, startLine, startColumn, endLine, endColumn, endExclusive)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppDiagnosticRange)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppDiagnosticRange
        
        if (filePath != other.filePath) return false
        if (startLine != other.startLine) return false
        if (startColumn != other.startColumn) return false
        if (endLine != other.endLine) return false
        if (endColumn != other.endColumn) return false
        if (endExclusive != other.endExclusive) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + filePath.hashCode()
        __r = __r*31 + startLine.hashCode()
        __r = __r*31 + startColumn.hashCode()
        __r = __r*31 + endLine.hashCode()
        __r = __r*31 + endColumn.hashCode()
        __r = __r*31 + endExclusive.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppDiagnosticRange (")
        printer.indent {
            print("filePath = "); filePath.print(printer); println()
            print("startLine = "); startLine.print(printer); println()
            print("startColumn = "); startColumn.print(printer); println()
            print("endLine = "); endLine.print(printer); println()
            print("endColumn = "); endColumn.print(printer); println()
            print("endExclusive = "); endExclusive.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:161]
 */
data class CppDiagnosticsDaemon (
    val state: String,
    val completionBasis: String,
    val processKind: String,
    val analysisScope: String,
    val stagePolicy: String,
    val contributingStages: Array<String>,
    val rawHighlightingCount: Int
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(state)
        buffer.writeString(completionBasis)
        buffer.writeString(processKind)
        buffer.writeString(analysisScope)
        buffer.writeString(stagePolicy)
        buffer.writeArray(contributingStages) { buffer.writeString(it) }
        buffer.writeInt(rawHighlightingCount)
    }
    //companion
    
    companion object : IMarshaller<CppDiagnosticsDaemon> {
        override val _type: KClass<CppDiagnosticsDaemon> = CppDiagnosticsDaemon::class
        override val id: RdId get() = RdId(2746252361769873568)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppDiagnosticsDaemon  {
            val state = buffer.readString()
            val completionBasis = buffer.readString()
            val processKind = buffer.readString()
            val analysisScope = buffer.readString()
            val stagePolicy = buffer.readString()
            val contributingStages = buffer.readArray {buffer.readString()}
            val rawHighlightingCount = buffer.readInt()
            return CppDiagnosticsDaemon(state, completionBasis, processKind, analysisScope, stagePolicy, contributingStages, rawHighlightingCount)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppDiagnosticsDaemon)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppDiagnosticsDaemon
        
        if (state != other.state) return false
        if (completionBasis != other.completionBasis) return false
        if (processKind != other.processKind) return false
        if (analysisScope != other.analysisScope) return false
        if (stagePolicy != other.stagePolicy) return false
        if (!(contributingStages contentDeepEquals other.contributingStages)) return false
        if (rawHighlightingCount != other.rawHighlightingCount) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + state.hashCode()
        __r = __r*31 + completionBasis.hashCode()
        __r = __r*31 + processKind.hashCode()
        __r = __r*31 + analysisScope.hashCode()
        __r = __r*31 + stagePolicy.hashCode()
        __r = __r*31 + contributingStages.contentDeepHashCode()
        __r = __r*31 + rawHighlightingCount.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppDiagnosticsDaemon (")
        printer.indent {
            print("state = "); state.print(printer); println()
            print("completionBasis = "); completionBasis.print(printer); println()
            print("processKind = "); processKind.print(printer); println()
            print("analysisScope = "); analysisScope.print(printer); println()
            print("stagePolicy = "); stagePolicy.print(printer); println()
            print("contributingStages = "); contributingStages.print(printer); println()
            print("rawHighlightingCount = "); rawHighlightingCount.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:171]
 */
data class CppDiagnosticsMetadata (
    val rawHighlightingCount: Int,
    val belowSeverityCount: Int,
    val outsidePositionCount: Int,
    val invalidRangeCount: Int,
    val unreadableSeverityCount: Int,
    val unreadableMetadataCount: Int,
    val unmappedRangeCount: Int,
    val matchedMappedCount: Int,
    val hasMatchedMappedCount: Boolean
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeInt(rawHighlightingCount)
        buffer.writeInt(belowSeverityCount)
        buffer.writeInt(outsidePositionCount)
        buffer.writeInt(invalidRangeCount)
        buffer.writeInt(unreadableSeverityCount)
        buffer.writeInt(unreadableMetadataCount)
        buffer.writeInt(unmappedRangeCount)
        buffer.writeInt(matchedMappedCount)
        buffer.writeBool(hasMatchedMappedCount)
    }
    //companion
    
    companion object : IMarshaller<CppDiagnosticsMetadata> {
        override val _type: KClass<CppDiagnosticsMetadata> = CppDiagnosticsMetadata::class
        override val id: RdId get() = RdId(1264117371964177995)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppDiagnosticsMetadata  {
            val rawHighlightingCount = buffer.readInt()
            val belowSeverityCount = buffer.readInt()
            val outsidePositionCount = buffer.readInt()
            val invalidRangeCount = buffer.readInt()
            val unreadableSeverityCount = buffer.readInt()
            val unreadableMetadataCount = buffer.readInt()
            val unmappedRangeCount = buffer.readInt()
            val matchedMappedCount = buffer.readInt()
            val hasMatchedMappedCount = buffer.readBool()
            return CppDiagnosticsMetadata(rawHighlightingCount, belowSeverityCount, outsidePositionCount, invalidRangeCount, unreadableSeverityCount, unreadableMetadataCount, unmappedRangeCount, matchedMappedCount, hasMatchedMappedCount)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppDiagnosticsMetadata)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppDiagnosticsMetadata
        
        if (rawHighlightingCount != other.rawHighlightingCount) return false
        if (belowSeverityCount != other.belowSeverityCount) return false
        if (outsidePositionCount != other.outsidePositionCount) return false
        if (invalidRangeCount != other.invalidRangeCount) return false
        if (unreadableSeverityCount != other.unreadableSeverityCount) return false
        if (unreadableMetadataCount != other.unreadableMetadataCount) return false
        if (unmappedRangeCount != other.unmappedRangeCount) return false
        if (matchedMappedCount != other.matchedMappedCount) return false
        if (hasMatchedMappedCount != other.hasMatchedMappedCount) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + rawHighlightingCount.hashCode()
        __r = __r*31 + belowSeverityCount.hashCode()
        __r = __r*31 + outsidePositionCount.hashCode()
        __r = __r*31 + invalidRangeCount.hashCode()
        __r = __r*31 + unreadableSeverityCount.hashCode()
        __r = __r*31 + unreadableMetadataCount.hashCode()
        __r = __r*31 + unmappedRangeCount.hashCode()
        __r = __r*31 + matchedMappedCount.hashCode()
        __r = __r*31 + hasMatchedMappedCount.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppDiagnosticsMetadata (")
        printer.indent {
            print("rawHighlightingCount = "); rawHighlightingCount.print(printer); println()
            print("belowSeverityCount = "); belowSeverityCount.print(printer); println()
            print("outsidePositionCount = "); outsidePositionCount.print(printer); println()
            print("invalidRangeCount = "); invalidRangeCount.print(printer); println()
            print("unreadableSeverityCount = "); unreadableSeverityCount.print(printer); println()
            print("unreadableMetadataCount = "); unreadableMetadataCount.print(printer); println()
            print("unmappedRangeCount = "); unmappedRangeCount.print(printer); println()
            print("matchedMappedCount = "); matchedMappedCount.print(printer); println()
            print("hasMatchedMappedCount = "); hasMatchedMappedCount.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:94]
 */
data class CppDiagnosticsRequest (
    val filePath: String,
    val hasPosition: Boolean,
    val line: Int,
    val column: Int,
    val minSeverity: String,
    val offset: Int,
    val maxResults: Int
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(filePath)
        buffer.writeBool(hasPosition)
        buffer.writeInt(line)
        buffer.writeInt(column)
        buffer.writeString(minSeverity)
        buffer.writeInt(offset)
        buffer.writeInt(maxResults)
    }
    //companion
    
    companion object : IMarshaller<CppDiagnosticsRequest> {
        override val _type: KClass<CppDiagnosticsRequest> = CppDiagnosticsRequest::class
        override val id: RdId get() = RdId(-7099897141130798093)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppDiagnosticsRequest  {
            val filePath = buffer.readString()
            val hasPosition = buffer.readBool()
            val line = buffer.readInt()
            val column = buffer.readInt()
            val minSeverity = buffer.readString()
            val offset = buffer.readInt()
            val maxResults = buffer.readInt()
            return CppDiagnosticsRequest(filePath, hasPosition, line, column, minSeverity, offset, maxResults)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppDiagnosticsRequest)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppDiagnosticsRequest
        
        if (filePath != other.filePath) return false
        if (hasPosition != other.hasPosition) return false
        if (line != other.line) return false
        if (column != other.column) return false
        if (minSeverity != other.minSeverity) return false
        if (offset != other.offset) return false
        if (maxResults != other.maxResults) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + filePath.hashCode()
        __r = __r*31 + hasPosition.hashCode()
        __r = __r*31 + line.hashCode()
        __r = __r*31 + column.hashCode()
        __r = __r*31 + minSeverity.hashCode()
        __r = __r*31 + offset.hashCode()
        __r = __r*31 + maxResults.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppDiagnosticsRequest (")
        printer.indent {
            print("filePath = "); filePath.print(printer); println()
            print("hasPosition = "); hasPosition.print(printer); println()
            print("line = "); line.print(printer); println()
            print("column = "); column.print(printer); println()
            print("minSeverity = "); minSeverity.print(printer); println()
            print("offset = "); offset.print(printer); println()
            print("maxResults = "); maxResults.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:136]
 */
data class CppDiagnosticsSourceFile (
    val isGeneratedFile: Boolean,
    val isNonUserFile: Boolean,
    val providesCodeModel: Boolean
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeBool(isGeneratedFile)
        buffer.writeBool(isNonUserFile)
        buffer.writeBool(providesCodeModel)
    }
    //companion
    
    companion object : IMarshaller<CppDiagnosticsSourceFile> {
        override val _type: KClass<CppDiagnosticsSourceFile> = CppDiagnosticsSourceFile::class
        override val id: RdId get() = RdId(-2668147198038132653)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppDiagnosticsSourceFile  {
            val isGeneratedFile = buffer.readBool()
            val isNonUserFile = buffer.readBool()
            val providesCodeModel = buffer.readBool()
            return CppDiagnosticsSourceFile(isGeneratedFile, isNonUserFile, providesCodeModel)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppDiagnosticsSourceFile)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppDiagnosticsSourceFile
        
        if (isGeneratedFile != other.isGeneratedFile) return false
        if (isNonUserFile != other.isNonUserFile) return false
        if (providesCodeModel != other.providesCodeModel) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + isGeneratedFile.hashCode()
        __r = __r*31 + isNonUserFile.hashCode()
        __r = __r*31 + providesCodeModel.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppDiagnosticsSourceFile (")
        printer.indent {
            print("isGeneratedFile = "); isGeneratedFile.print(printer); println()
            print("isNonUserFile = "); isNonUserFile.print(printer); println()
            print("providesCodeModel = "); providesCodeModel.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:84]
 */
data class CppFileOutlineRequest (
    val filePath: String,
    val offset: Int,
    val maxResults: Int
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(filePath)
        buffer.writeInt(offset)
        buffer.writeInt(maxResults)
    }
    //companion
    
    companion object : IMarshaller<CppFileOutlineRequest> {
        override val _type: KClass<CppFileOutlineRequest> = CppFileOutlineRequest::class
        override val id: RdId get() = RdId(-1321815825372745415)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppFileOutlineRequest  {
            val filePath = buffer.readString()
            val offset = buffer.readInt()
            val maxResults = buffer.readInt()
            return CppFileOutlineRequest(filePath, offset, maxResults)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppFileOutlineRequest)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppFileOutlineRequest
        
        if (filePath != other.filePath) return false
        if (offset != other.offset) return false
        if (maxResults != other.maxResults) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + filePath.hashCode()
        __r = __r*31 + offset.hashCode()
        __r = __r*31 + maxResults.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppFileOutlineRequest (")
        printer.indent {
            print("filePath = "); filePath.print(printer); println()
            print("offset = "); offset.print(printer); println()
            print("maxResults = "); maxResults.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:90]
 */
data class CppFileSemanticStateRequest (
    val filePath: String
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(filePath)
    }
    //companion
    
    companion object : IMarshaller<CppFileSemanticStateRequest> {
        override val _type: KClass<CppFileSemanticStateRequest> = CppFileSemanticStateRequest::class
        override val id: RdId get() = RdId(-4751034183951864336)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppFileSemanticStateRequest  {
            val filePath = buffer.readString()
            return CppFileSemanticStateRequest(filePath)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppFileSemanticStateRequest)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppFileSemanticStateRequest
        
        if (filePath != other.filePath) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + filePath.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppFileSemanticStateRequest (")
        printer.indent {
            print("filePath = "); filePath.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:235]
 */
data class CppFileSemanticStateResponse (
    val status: String,
    val filePath: String,
    val cppPsiSourceRegistered: Boolean,
    val primaryCppPsiAvailable: Boolean,
    val providesCodeModel: Boolean,
    val primaryPsiLanguage: String,
    val diagnostics: Array<CppSemanticDiagnostic>
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(status)
        buffer.writeString(filePath)
        buffer.writeBool(cppPsiSourceRegistered)
        buffer.writeBool(primaryCppPsiAvailable)
        buffer.writeBool(providesCodeModel)
        buffer.writeString(primaryPsiLanguage)
        buffer.writeArray(diagnostics) { CppSemanticDiagnostic.write(ctx, buffer, it) }
    }
    //companion
    
    companion object : IMarshaller<CppFileSemanticStateResponse> {
        override val _type: KClass<CppFileSemanticStateResponse> = CppFileSemanticStateResponse::class
        override val id: RdId get() = RdId(291892887221552384)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppFileSemanticStateResponse  {
            val status = buffer.readString()
            val filePath = buffer.readString()
            val cppPsiSourceRegistered = buffer.readBool()
            val primaryCppPsiAvailable = buffer.readBool()
            val providesCodeModel = buffer.readBool()
            val primaryPsiLanguage = buffer.readString()
            val diagnostics = buffer.readArray {CppSemanticDiagnostic.read(ctx, buffer)}
            return CppFileSemanticStateResponse(status, filePath, cppPsiSourceRegistered, primaryCppPsiAvailable, providesCodeModel, primaryPsiLanguage, diagnostics)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppFileSemanticStateResponse)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppFileSemanticStateResponse
        
        if (status != other.status) return false
        if (filePath != other.filePath) return false
        if (cppPsiSourceRegistered != other.cppPsiSourceRegistered) return false
        if (primaryCppPsiAvailable != other.primaryCppPsiAvailable) return false
        if (providesCodeModel != other.providesCodeModel) return false
        if (primaryPsiLanguage != other.primaryPsiLanguage) return false
        if (!(diagnostics contentDeepEquals other.diagnostics)) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + status.hashCode()
        __r = __r*31 + filePath.hashCode()
        __r = __r*31 + cppPsiSourceRegistered.hashCode()
        __r = __r*31 + primaryCppPsiAvailable.hashCode()
        __r = __r*31 + providesCodeModel.hashCode()
        __r = __r*31 + primaryPsiLanguage.hashCode()
        __r = __r*31 + diagnostics.contentDeepHashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppFileSemanticStateResponse (")
        printer.indent {
            print("status = "); status.print(printer); println()
            print("filePath = "); filePath.print(printer); println()
            print("cppPsiSourceRegistered = "); cppPsiSourceRegistered.print(printer); println()
            print("primaryCppPsiAvailable = "); primaryCppPsiAvailable.print(printer); println()
            print("providesCodeModel = "); providesCodeModel.print(printer); println()
            print("primaryPsiLanguage = "); primaryPsiLanguage.print(printer); println()
            print("diagnostics = "); diagnostics.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:123]
 */
data class CppFileSymbolOccurrence (
    val outlineIndex: Int,
    val parentOutlineIndex: Int,
    val depth: Int,
    val name: String,
    val qualifiedName: String,
    val kind: String,
    val signature: String,
    val containingType: String,
    val declarationRole: String,
    val location: CppSourcePosition
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeInt(outlineIndex)
        buffer.writeInt(parentOutlineIndex)
        buffer.writeInt(depth)
        buffer.writeString(name)
        buffer.writeString(qualifiedName)
        buffer.writeString(kind)
        buffer.writeString(signature)
        buffer.writeString(containingType)
        buffer.writeString(declarationRole)
        CppSourcePosition.write(ctx, buffer, location)
    }
    //companion
    
    companion object : IMarshaller<CppFileSymbolOccurrence> {
        override val _type: KClass<CppFileSymbolOccurrence> = CppFileSymbolOccurrence::class
        override val id: RdId get() = RdId(-3544344833785145387)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppFileSymbolOccurrence  {
            val outlineIndex = buffer.readInt()
            val parentOutlineIndex = buffer.readInt()
            val depth = buffer.readInt()
            val name = buffer.readString()
            val qualifiedName = buffer.readString()
            val kind = buffer.readString()
            val signature = buffer.readString()
            val containingType = buffer.readString()
            val declarationRole = buffer.readString()
            val location = CppSourcePosition.read(ctx, buffer)
            return CppFileSymbolOccurrence(outlineIndex, parentOutlineIndex, depth, name, qualifiedName, kind, signature, containingType, declarationRole, location)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppFileSymbolOccurrence)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppFileSymbolOccurrence
        
        if (outlineIndex != other.outlineIndex) return false
        if (parentOutlineIndex != other.parentOutlineIndex) return false
        if (depth != other.depth) return false
        if (name != other.name) return false
        if (qualifiedName != other.qualifiedName) return false
        if (kind != other.kind) return false
        if (signature != other.signature) return false
        if (containingType != other.containingType) return false
        if (declarationRole != other.declarationRole) return false
        if (location != other.location) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + outlineIndex.hashCode()
        __r = __r*31 + parentOutlineIndex.hashCode()
        __r = __r*31 + depth.hashCode()
        __r = __r*31 + name.hashCode()
        __r = __r*31 + qualifiedName.hashCode()
        __r = __r*31 + kind.hashCode()
        __r = __r*31 + signature.hashCode()
        __r = __r*31 + containingType.hashCode()
        __r = __r*31 + declarationRole.hashCode()
        __r = __r*31 + location.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppFileSymbolOccurrence (")
        printer.indent {
            print("outlineIndex = "); outlineIndex.print(printer); println()
            print("parentOutlineIndex = "); parentOutlineIndex.print(printer); println()
            print("depth = "); depth.print(printer); println()
            print("name = "); name.print(printer); println()
            print("qualifiedName = "); qualifiedName.print(printer); println()
            print("kind = "); kind.print(printer); println()
            print("signature = "); signature.print(printer); println()
            print("containingType = "); containingType.print(printer); println()
            print("declarationRole = "); declarationRole.print(printer); println()
            print("location = "); location.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:53]
 */
data class CppInspectedSymbol (
    val summary: CppSymbolSummary,
    val declarations: Array<CppSourcePosition>,
    val definitions: Array<CppSourcePosition>,
    val accessibility: String,
    val isVirtual: String,
    val isPureVirtual: String,
    val isFinal: String
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        CppSymbolSummary.write(ctx, buffer, summary)
        buffer.writeArray(declarations) { CppSourcePosition.write(ctx, buffer, it) }
        buffer.writeArray(definitions) { CppSourcePosition.write(ctx, buffer, it) }
        buffer.writeString(accessibility)
        buffer.writeString(isVirtual)
        buffer.writeString(isPureVirtual)
        buffer.writeString(isFinal)
    }
    //companion
    
    companion object : IMarshaller<CppInspectedSymbol> {
        override val _type: KClass<CppInspectedSymbol> = CppInspectedSymbol::class
        override val id: RdId get() = RdId(-5682256638303559045)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppInspectedSymbol  {
            val summary = CppSymbolSummary.read(ctx, buffer)
            val declarations = buffer.readArray {CppSourcePosition.read(ctx, buffer)}
            val definitions = buffer.readArray {CppSourcePosition.read(ctx, buffer)}
            val accessibility = buffer.readString()
            val isVirtual = buffer.readString()
            val isPureVirtual = buffer.readString()
            val isFinal = buffer.readString()
            return CppInspectedSymbol(summary, declarations, definitions, accessibility, isVirtual, isPureVirtual, isFinal)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppInspectedSymbol)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppInspectedSymbol
        
        if (summary != other.summary) return false
        if (!(declarations contentDeepEquals other.declarations)) return false
        if (!(definitions contentDeepEquals other.definitions)) return false
        if (accessibility != other.accessibility) return false
        if (isVirtual != other.isVirtual) return false
        if (isPureVirtual != other.isPureVirtual) return false
        if (isFinal != other.isFinal) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + summary.hashCode()
        __r = __r*31 + declarations.contentDeepHashCode()
        __r = __r*31 + definitions.contentDeepHashCode()
        __r = __r*31 + accessibility.hashCode()
        __r = __r*31 + isVirtual.hashCode()
        __r = __r*31 + isPureVirtual.hashCode()
        __r = __r*31 + isFinal.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppInspectedSymbol (")
        printer.indent {
            print("summary = "); summary.print(printer); println()
            print("declarations = "); declarations.print(printer); println()
            print("definitions = "); definitions.print(printer); println()
            print("accessibility = "); accessibility.print(printer); println()
            print("isVirtual = "); isVirtual.print(printer); println()
            print("isPureVirtual = "); isPureVirtual.print(printer); println()
            print("isFinal = "); isFinal.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:30]
 */
data class CppPageInfo (
    val offset: Int,
    val returnedCount: Int,
    val totalMappedCount: Int,
    val hasTotalMappedCount: Boolean,
    val hasMore: Boolean,
    val nextOffset: Int,
    val skippedCount: Int
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeInt(offset)
        buffer.writeInt(returnedCount)
        buffer.writeInt(totalMappedCount)
        buffer.writeBool(hasTotalMappedCount)
        buffer.writeBool(hasMore)
        buffer.writeInt(nextOffset)
        buffer.writeInt(skippedCount)
    }
    //companion
    
    companion object : IMarshaller<CppPageInfo> {
        override val _type: KClass<CppPageInfo> = CppPageInfo::class
        override val id: RdId get() = RdId(540735207878941837)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppPageInfo  {
            val offset = buffer.readInt()
            val returnedCount = buffer.readInt()
            val totalMappedCount = buffer.readInt()
            val hasTotalMappedCount = buffer.readBool()
            val hasMore = buffer.readBool()
            val nextOffset = buffer.readInt()
            val skippedCount = buffer.readInt()
            return CppPageInfo(offset, returnedCount, totalMappedCount, hasTotalMappedCount, hasMore, nextOffset, skippedCount)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppPageInfo)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppPageInfo
        
        if (offset != other.offset) return false
        if (returnedCount != other.returnedCount) return false
        if (totalMappedCount != other.totalMappedCount) return false
        if (hasTotalMappedCount != other.hasTotalMappedCount) return false
        if (hasMore != other.hasMore) return false
        if (nextOffset != other.nextOffset) return false
        if (skippedCount != other.skippedCount) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + offset.hashCode()
        __r = __r*31 + returnedCount.hashCode()
        __r = __r*31 + totalMappedCount.hashCode()
        __r = __r*31 + hasTotalMappedCount.hashCode()
        __r = __r*31 + hasMore.hashCode()
        __r = __r*31 + nextOffset.hashCode()
        __r = __r*31 + skippedCount.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppPageInfo (")
        printer.indent {
            print("offset = "); offset.print(printer); println()
            print("returnedCount = "); returnedCount.print(printer); println()
            print("totalMappedCount = "); totalMappedCount.print(printer); println()
            print("hasTotalMappedCount = "); hasTotalMappedCount.print(printer); println()
            print("hasMore = "); hasMore.print(printer); println()
            print("nextOffset = "); nextOffset.print(printer); println()
            print("skippedCount = "); skippedCount.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:69]
 */
data class CppPagedTargetRequest (
    val filePath: String,
    val line: Int,
    val column: Int,
    val offset: Int,
    val maxResults: Int
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(filePath)
        buffer.writeInt(line)
        buffer.writeInt(column)
        buffer.writeInt(offset)
        buffer.writeInt(maxResults)
    }
    //companion
    
    companion object : IMarshaller<CppPagedTargetRequest> {
        override val _type: KClass<CppPagedTargetRequest> = CppPagedTargetRequest::class
        override val id: RdId get() = RdId(5998817664576190137)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppPagedTargetRequest  {
            val filePath = buffer.readString()
            val line = buffer.readInt()
            val column = buffer.readInt()
            val offset = buffer.readInt()
            val maxResults = buffer.readInt()
            return CppPagedTargetRequest(filePath, line, column, offset, maxResults)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppPagedTargetRequest)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppPagedTargetRequest
        
        if (filePath != other.filePath) return false
        if (line != other.line) return false
        if (column != other.column) return false
        if (offset != other.offset) return false
        if (maxResults != other.maxResults) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + filePath.hashCode()
        __r = __r*31 + line.hashCode()
        __r = __r*31 + column.hashCode()
        __r = __r*31 + offset.hashCode()
        __r = __r*31 + maxResults.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppPagedTargetRequest (")
        printer.indent {
            print("filePath = "); filePath.print(printer); println()
            print("line = "); line.print(printer); println()
            print("column = "); column.print(printer); println()
            print("offset = "); offset.print(printer); println()
            print("maxResults = "); maxResults.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:104]
 */
data class CppReferenceResult (
    val location: CppSourcePosition,
    val context: String,
    val usageKind: String
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        CppSourcePosition.write(ctx, buffer, location)
        buffer.writeString(context)
        buffer.writeString(usageKind)
    }
    //companion
    
    companion object : IMarshaller<CppReferenceResult> {
        override val _type: KClass<CppReferenceResult> = CppReferenceResult::class
        override val id: RdId get() = RdId(-8940797202119366504)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppReferenceResult  {
            val location = CppSourcePosition.read(ctx, buffer)
            val context = buffer.readString()
            val usageKind = buffer.readString()
            return CppReferenceResult(location, context, usageKind)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppReferenceResult)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppReferenceResult
        
        if (location != other.location) return false
        if (context != other.context) return false
        if (usageKind != other.usageKind) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + location.hashCode()
        __r = __r*31 + context.hashCode()
        __r = __r*31 + usageKind.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppReferenceResult (")
        printer.indent {
            print("location = "); location.print(printer); println()
            print("context = "); context.print(printer); println()
            print("usageKind = "); usageKind.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:118]
 */
data class CppRelatedSymbolResult (
    val symbol: CppSymbolSummary,
    val relation: String
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        CppSymbolSummary.write(ctx, buffer, symbol)
        buffer.writeString(relation)
    }
    //companion
    
    companion object : IMarshaller<CppRelatedSymbolResult> {
        override val _type: KClass<CppRelatedSymbolResult> = CppRelatedSymbolResult::class
        override val id: RdId get() = RdId(-1907257425276055312)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppRelatedSymbolResult  {
            val symbol = CppSymbolSummary.read(ctx, buffer)
            val relation = buffer.readString()
            return CppRelatedSymbolResult(symbol, relation)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppRelatedSymbolResult)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppRelatedSymbolResult
        
        if (symbol != other.symbol) return false
        if (relation != other.relation) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + symbol.hashCode()
        __r = __r*31 + relation.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppRelatedSymbolResult (")
        printer.indent {
            print("symbol = "); symbol.print(printer); println()
            print("relation = "); relation.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:25]
 */
data class CppSemanticDiagnostic (
    val code: String,
    val message: String
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(code)
        buffer.writeString(message)
    }
    //companion
    
    companion object : IMarshaller<CppSemanticDiagnostic> {
        override val _type: KClass<CppSemanticDiagnostic> = CppSemanticDiagnostic::class
        override val id: RdId get() = RdId(-5271211828164851011)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppSemanticDiagnostic  {
            val code = buffer.readString()
            val message = buffer.readString()
            return CppSemanticDiagnostic(code, message)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppSemanticDiagnostic)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppSemanticDiagnostic
        
        if (code != other.code) return false
        if (message != other.message) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + code.hashCode()
        __r = __r*31 + message.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppSemanticDiagnostic (")
        printer.indent {
            print("code = "); code.print(printer); println()
            print("message = "); message.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:19]
 */
data class CppSourcePosition (
    val filePath: String,
    val line: Int,
    val column: Int
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(filePath)
        buffer.writeInt(line)
        buffer.writeInt(column)
    }
    //companion
    
    companion object : IMarshaller<CppSourcePosition> {
        override val _type: KClass<CppSourcePosition> = CppSourcePosition::class
        override val id: RdId get() = RdId(-330948089425930476)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppSourcePosition  {
            val filePath = buffer.readString()
            val line = buffer.readInt()
            val column = buffer.readInt()
            return CppSourcePosition(filePath, line, column)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppSourcePosition)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppSourcePosition
        
        if (filePath != other.filePath) return false
        if (line != other.line) return false
        if (column != other.column) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + filePath.hashCode()
        __r = __r*31 + line.hashCode()
        __r = __r*31 + column.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppSourcePosition (")
        printer.indent {
            print("filePath = "); filePath.print(printer); println()
            print("line = "); line.print(printer); println()
            print("column = "); column.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:40]
 */
data class CppSymbolSummary (
    val name: String,
    val qualifiedName: String,
    val kind: String,
    val signature: String,
    val containingType: String,
    val ueModule: String,
    val navigation: CppSourcePosition,
    val hasNavigation: Boolean,
    val declarationCount: Int,
    val definitionCount: Int
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(name)
        buffer.writeString(qualifiedName)
        buffer.writeString(kind)
        buffer.writeString(signature)
        buffer.writeString(containingType)
        buffer.writeString(ueModule)
        CppSourcePosition.write(ctx, buffer, navigation)
        buffer.writeBool(hasNavigation)
        buffer.writeInt(declarationCount)
        buffer.writeInt(definitionCount)
    }
    //companion
    
    companion object : IMarshaller<CppSymbolSummary> {
        override val _type: KClass<CppSymbolSummary> = CppSymbolSummary::class
        override val id: RdId get() = RdId(8567227040194444350)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppSymbolSummary  {
            val name = buffer.readString()
            val qualifiedName = buffer.readString()
            val kind = buffer.readString()
            val signature = buffer.readString()
            val containingType = buffer.readString()
            val ueModule = buffer.readString()
            val navigation = CppSourcePosition.read(ctx, buffer)
            val hasNavigation = buffer.readBool()
            val declarationCount = buffer.readInt()
            val definitionCount = buffer.readInt()
            return CppSymbolSummary(name, qualifiedName, kind, signature, containingType, ueModule, navigation, hasNavigation, declarationCount, definitionCount)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppSymbolSummary)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppSymbolSummary
        
        if (name != other.name) return false
        if (qualifiedName != other.qualifiedName) return false
        if (kind != other.kind) return false
        if (signature != other.signature) return false
        if (containingType != other.containingType) return false
        if (ueModule != other.ueModule) return false
        if (navigation != other.navigation) return false
        if (hasNavigation != other.hasNavigation) return false
        if (declarationCount != other.declarationCount) return false
        if (definitionCount != other.definitionCount) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + name.hashCode()
        __r = __r*31 + qualifiedName.hashCode()
        __r = __r*31 + kind.hashCode()
        __r = __r*31 + signature.hashCode()
        __r = __r*31 + containingType.hashCode()
        __r = __r*31 + ueModule.hashCode()
        __r = __r*31 + navigation.hashCode()
        __r = __r*31 + hasNavigation.hashCode()
        __r = __r*31 + declarationCount.hashCode()
        __r = __r*31 + definitionCount.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppSymbolSummary (")
        printer.indent {
            print("name = "); name.print(printer); println()
            print("qualifiedName = "); qualifiedName.print(printer); println()
            print("kind = "); kind.print(printer); println()
            print("signature = "); signature.print(printer); println()
            print("containingType = "); containingType.print(printer); println()
            print("ueModule = "); ueModule.print(printer); println()
            print("navigation = "); navigation.print(printer); println()
            print("hasNavigation = "); hasNavigation.print(printer); println()
            print("declarationCount = "); declarationCount.print(printer); println()
            print("definitionCount = "); definitionCount.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:63]
 */
data class CppTargetRequest (
    val filePath: String,
    val line: Int,
    val column: Int
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(filePath)
        buffer.writeInt(line)
        buffer.writeInt(column)
    }
    //companion
    
    companion object : IMarshaller<CppTargetRequest> {
        override val _type: KClass<CppTargetRequest> = CppTargetRequest::class
        override val id: RdId get() = RdId(8749308407863890190)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CppTargetRequest  {
            val filePath = buffer.readString()
            val line = buffer.readInt()
            val column = buffer.readInt()
            return CppTargetRequest(filePath, line, column)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CppTargetRequest)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as CppTargetRequest
        
        if (filePath != other.filePath) return false
        if (line != other.line) return false
        if (column != other.column) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + filePath.hashCode()
        __r = __r*31 + line.hashCode()
        __r = __r*31 + column.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CppTargetRequest (")
        printer.indent {
            print("filePath = "); filePath.print(printer); println()
            print("line = "); line.print(printer); println()
            print("column = "); column.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:198]
 */
data class FindReferencesResponse (
    val status: String,
    val targets: Array<CppSymbolSummary>,
    val references: Array<CppReferenceResult>,
    val page: CppPageInfo,
    val searchScope: String,
    val diagnostics: Array<CppSemanticDiagnostic>
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(status)
        buffer.writeArray(targets) { CppSymbolSummary.write(ctx, buffer, it) }
        buffer.writeArray(references) { CppReferenceResult.write(ctx, buffer, it) }
        CppPageInfo.write(ctx, buffer, page)
        buffer.writeString(searchScope)
        buffer.writeArray(diagnostics) { CppSemanticDiagnostic.write(ctx, buffer, it) }
    }
    //companion
    
    companion object : IMarshaller<FindReferencesResponse> {
        override val _type: KClass<FindReferencesResponse> = FindReferencesResponse::class
        override val id: RdId get() = RdId(-4750332656891773835)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): FindReferencesResponse  {
            val status = buffer.readString()
            val targets = buffer.readArray {CppSymbolSummary.read(ctx, buffer)}
            val references = buffer.readArray {CppReferenceResult.read(ctx, buffer)}
            val page = CppPageInfo.read(ctx, buffer)
            val searchScope = buffer.readString()
            val diagnostics = buffer.readArray {CppSemanticDiagnostic.read(ctx, buffer)}
            return FindReferencesResponse(status, targets, references, page, searchScope, diagnostics)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: FindReferencesResponse)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as FindReferencesResponse
        
        if (status != other.status) return false
        if (!(targets contentDeepEquals other.targets)) return false
        if (!(references contentDeepEquals other.references)) return false
        if (page != other.page) return false
        if (searchScope != other.searchScope) return false
        if (!(diagnostics contentDeepEquals other.diagnostics)) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + status.hashCode()
        __r = __r*31 + targets.contentDeepHashCode()
        __r = __r*31 + references.contentDeepHashCode()
        __r = __r*31 + page.hashCode()
        __r = __r*31 + searchScope.hashCode()
        __r = __r*31 + diagnostics.contentDeepHashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("FindReferencesResponse (")
        printer.indent {
            print("status = "); status.print(printer); println()
            print("targets = "); targets.print(printer); println()
            print("references = "); references.print(printer); println()
            print("page = "); page.print(printer); println()
            print("searchScope = "); searchScope.print(printer); println()
            print("diagnostics = "); diagnostics.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:245]
 */
data class GetDiagnosticsResponse (
    val status: String,
    val filePath: String,
    val psiContext: String,
    val primaryPsiLanguage: String,
    val indexEvidence: String,
    val hasSourceFile: Boolean,
    val sourceFile: CppDiagnosticsSourceFile,
    val hasDaemon: Boolean,
    val daemon: CppDiagnosticsDaemon,
    val findings: Array<CppDiagnosticFinding>,
    val page: CppPageInfo,
    val findingsMetadata: CppDiagnosticsMetadata,
    val diagnostics: Array<CppSemanticDiagnostic>
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(status)
        buffer.writeString(filePath)
        buffer.writeString(psiContext)
        buffer.writeString(primaryPsiLanguage)
        buffer.writeString(indexEvidence)
        buffer.writeBool(hasSourceFile)
        CppDiagnosticsSourceFile.write(ctx, buffer, sourceFile)
        buffer.writeBool(hasDaemon)
        CppDiagnosticsDaemon.write(ctx, buffer, daemon)
        buffer.writeArray(findings) { CppDiagnosticFinding.write(ctx, buffer, it) }
        CppPageInfo.write(ctx, buffer, page)
        CppDiagnosticsMetadata.write(ctx, buffer, findingsMetadata)
        buffer.writeArray(diagnostics) { CppSemanticDiagnostic.write(ctx, buffer, it) }
    }
    //companion
    
    companion object : IMarshaller<GetDiagnosticsResponse> {
        override val _type: KClass<GetDiagnosticsResponse> = GetDiagnosticsResponse::class
        override val id: RdId get() = RdId(7451040521766080202)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): GetDiagnosticsResponse  {
            val status = buffer.readString()
            val filePath = buffer.readString()
            val psiContext = buffer.readString()
            val primaryPsiLanguage = buffer.readString()
            val indexEvidence = buffer.readString()
            val hasSourceFile = buffer.readBool()
            val sourceFile = CppDiagnosticsSourceFile.read(ctx, buffer)
            val hasDaemon = buffer.readBool()
            val daemon = CppDiagnosticsDaemon.read(ctx, buffer)
            val findings = buffer.readArray {CppDiagnosticFinding.read(ctx, buffer)}
            val page = CppPageInfo.read(ctx, buffer)
            val findingsMetadata = CppDiagnosticsMetadata.read(ctx, buffer)
            val diagnostics = buffer.readArray {CppSemanticDiagnostic.read(ctx, buffer)}
            return GetDiagnosticsResponse(status, filePath, psiContext, primaryPsiLanguage, indexEvidence, hasSourceFile, sourceFile, hasDaemon, daemon, findings, page, findingsMetadata, diagnostics)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: GetDiagnosticsResponse)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as GetDiagnosticsResponse
        
        if (status != other.status) return false
        if (filePath != other.filePath) return false
        if (psiContext != other.psiContext) return false
        if (primaryPsiLanguage != other.primaryPsiLanguage) return false
        if (indexEvidence != other.indexEvidence) return false
        if (hasSourceFile != other.hasSourceFile) return false
        if (sourceFile != other.sourceFile) return false
        if (hasDaemon != other.hasDaemon) return false
        if (daemon != other.daemon) return false
        if (!(findings contentDeepEquals other.findings)) return false
        if (page != other.page) return false
        if (findingsMetadata != other.findingsMetadata) return false
        if (!(diagnostics contentDeepEquals other.diagnostics)) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + status.hashCode()
        __r = __r*31 + filePath.hashCode()
        __r = __r*31 + psiContext.hashCode()
        __r = __r*31 + primaryPsiLanguage.hashCode()
        __r = __r*31 + indexEvidence.hashCode()
        __r = __r*31 + hasSourceFile.hashCode()
        __r = __r*31 + sourceFile.hashCode()
        __r = __r*31 + hasDaemon.hashCode()
        __r = __r*31 + daemon.hashCode()
        __r = __r*31 + findings.contentDeepHashCode()
        __r = __r*31 + page.hashCode()
        __r = __r*31 + findingsMetadata.hashCode()
        __r = __r*31 + diagnostics.contentDeepHashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("GetDiagnosticsResponse (")
        printer.indent {
            print("status = "); status.print(printer); println()
            print("filePath = "); filePath.print(printer); println()
            print("psiContext = "); psiContext.print(printer); println()
            print("primaryPsiLanguage = "); primaryPsiLanguage.print(printer); println()
            print("indexEvidence = "); indexEvidence.print(printer); println()
            print("hasSourceFile = "); hasSourceFile.print(printer); println()
            print("sourceFile = "); sourceFile.print(printer); println()
            print("hasDaemon = "); hasDaemon.print(printer); println()
            print("daemon = "); daemon.print(printer); println()
            print("findings = "); findings.print(printer); println()
            print("page = "); page.print(printer); println()
            print("findingsMetadata = "); findingsMetadata.print(printer); println()
            print("diagnostics = "); diagnostics.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:183]
 */
data class InspectSymbolResponse (
    val status: String,
    val symbols: Array<CppInspectedSymbol>,
    val searchScope: String,
    val diagnostics: Array<CppSemanticDiagnostic>
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(status)
        buffer.writeArray(symbols) { CppInspectedSymbol.write(ctx, buffer, it) }
        buffer.writeString(searchScope)
        buffer.writeArray(diagnostics) { CppSemanticDiagnostic.write(ctx, buffer, it) }
    }
    //companion
    
    companion object : IMarshaller<InspectSymbolResponse> {
        override val _type: KClass<InspectSymbolResponse> = InspectSymbolResponse::class
        override val id: RdId get() = RdId(-2301226452322223398)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): InspectSymbolResponse  {
            val status = buffer.readString()
            val symbols = buffer.readArray {CppInspectedSymbol.read(ctx, buffer)}
            val searchScope = buffer.readString()
            val diagnostics = buffer.readArray {CppSemanticDiagnostic.read(ctx, buffer)}
            return InspectSymbolResponse(status, symbols, searchScope, diagnostics)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: InspectSymbolResponse)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as InspectSymbolResponse
        
        if (status != other.status) return false
        if (!(symbols contentDeepEquals other.symbols)) return false
        if (searchScope != other.searchScope) return false
        if (!(diagnostics contentDeepEquals other.diagnostics)) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + status.hashCode()
        __r = __r*31 + symbols.contentDeepHashCode()
        __r = __r*31 + searchScope.hashCode()
        __r = __r*31 + diagnostics.contentDeepHashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("InspectSymbolResponse (")
        printer.indent {
            print("status = "); status.print(printer); println()
            print("symbols = "); symbols.print(printer); println()
            print("searchScope = "); searchScope.print(printer); println()
            print("diagnostics = "); diagnostics.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:225]
 */
data class ListSymbolsInFileResponse (
    val status: String,
    val filePath: String,
    val ueModule: String,
    val psiContext: String,
    val symbols: Array<CppFileSymbolOccurrence>,
    val page: CppPageInfo,
    val diagnostics: Array<CppSemanticDiagnostic>
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(status)
        buffer.writeString(filePath)
        buffer.writeString(ueModule)
        buffer.writeString(psiContext)
        buffer.writeArray(symbols) { CppFileSymbolOccurrence.write(ctx, buffer, it) }
        CppPageInfo.write(ctx, buffer, page)
        buffer.writeArray(diagnostics) { CppSemanticDiagnostic.write(ctx, buffer, it) }
    }
    //companion
    
    companion object : IMarshaller<ListSymbolsInFileResponse> {
        override val _type: KClass<ListSymbolsInFileResponse> = ListSymbolsInFileResponse::class
        override val id: RdId get() = RdId(7030377938158652300)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ListSymbolsInFileResponse  {
            val status = buffer.readString()
            val filePath = buffer.readString()
            val ueModule = buffer.readString()
            val psiContext = buffer.readString()
            val symbols = buffer.readArray {CppFileSymbolOccurrence.read(ctx, buffer)}
            val page = CppPageInfo.read(ctx, buffer)
            val diagnostics = buffer.readArray {CppSemanticDiagnostic.read(ctx, buffer)}
            return ListSymbolsInFileResponse(status, filePath, ueModule, psiContext, symbols, page, diagnostics)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ListSymbolsInFileResponse)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as ListSymbolsInFileResponse
        
        if (status != other.status) return false
        if (filePath != other.filePath) return false
        if (ueModule != other.ueModule) return false
        if (psiContext != other.psiContext) return false
        if (!(symbols contentDeepEquals other.symbols)) return false
        if (page != other.page) return false
        if (!(diagnostics contentDeepEquals other.diagnostics)) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + status.hashCode()
        __r = __r*31 + filePath.hashCode()
        __r = __r*31 + ueModule.hashCode()
        __r = __r*31 + psiContext.hashCode()
        __r = __r*31 + symbols.contentDeepHashCode()
        __r = __r*31 + page.hashCode()
        __r = __r*31 + diagnostics.contentDeepHashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ListSymbolsInFileResponse (")
        printer.indent {
            print("status = "); status.print(printer); println()
            print("filePath = "); filePath.print(printer); println()
            print("ueModule = "); ueModule.print(printer); println()
            print("psiContext = "); psiContext.print(printer); println()
            print("symbols = "); symbols.print(printer); println()
            print("page = "); page.print(printer); println()
            print("diagnostics = "); diagnostics.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:216]
 */
data class RelatedSymbolsResponse (
    val status: String,
    val targets: Array<CppSymbolSummary>,
    val symbols: Array<CppRelatedSymbolResult>,
    val page: CppPageInfo,
    val searchScope: String,
    val diagnostics: Array<CppSemanticDiagnostic>
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(status)
        buffer.writeArray(targets) { CppSymbolSummary.write(ctx, buffer, it) }
        buffer.writeArray(symbols) { CppRelatedSymbolResult.write(ctx, buffer, it) }
        CppPageInfo.write(ctx, buffer, page)
        buffer.writeString(searchScope)
        buffer.writeArray(diagnostics) { CppSemanticDiagnostic.write(ctx, buffer, it) }
    }
    //companion
    
    companion object : IMarshaller<RelatedSymbolsResponse> {
        override val _type: KClass<RelatedSymbolsResponse> = RelatedSymbolsResponse::class
        override val id: RdId get() = RdId(-6339707347220064380)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): RelatedSymbolsResponse  {
            val status = buffer.readString()
            val targets = buffer.readArray {CppSymbolSummary.read(ctx, buffer)}
            val symbols = buffer.readArray {CppRelatedSymbolResult.read(ctx, buffer)}
            val page = CppPageInfo.read(ctx, buffer)
            val searchScope = buffer.readString()
            val diagnostics = buffer.readArray {CppSemanticDiagnostic.read(ctx, buffer)}
            return RelatedSymbolsResponse(status, targets, symbols, page, searchScope, diagnostics)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: RelatedSymbolsResponse)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as RelatedSymbolsResponse
        
        if (status != other.status) return false
        if (!(targets contentDeepEquals other.targets)) return false
        if (!(symbols contentDeepEquals other.symbols)) return false
        if (page != other.page) return false
        if (searchScope != other.searchScope) return false
        if (!(diagnostics contentDeepEquals other.diagnostics)) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + status.hashCode()
        __r = __r*31 + targets.contentDeepHashCode()
        __r = __r*31 + symbols.contentDeepHashCode()
        __r = __r*31 + page.hashCode()
        __r = __r*31 + searchScope.hashCode()
        __r = __r*31 + diagnostics.contentDeepHashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("RelatedSymbolsResponse (")
        printer.indent {
            print("status = "); status.print(printer); println()
            print("targets = "); targets.print(printer); println()
            print("symbols = "); symbols.print(printer); println()
            print("page = "); page.print(printer); println()
            print("searchScope = "); searchScope.print(printer); println()
            print("diagnostics = "); diagnostics.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:77]
 */
data class SearchSymbolsRequest (
    val name: String,
    val kinds: Array<String>,
    val offset: Int,
    val maxResults: Int
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(name)
        buffer.writeArray(kinds) { buffer.writeString(it) }
        buffer.writeInt(offset)
        buffer.writeInt(maxResults)
    }
    //companion
    
    companion object : IMarshaller<SearchSymbolsRequest> {
        override val _type: KClass<SearchSymbolsRequest> = SearchSymbolsRequest::class
        override val id: RdId get() = RdId(8998747142779480239)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): SearchSymbolsRequest  {
            val name = buffer.readString()
            val kinds = buffer.readArray {buffer.readString()}
            val offset = buffer.readInt()
            val maxResults = buffer.readInt()
            return SearchSymbolsRequest(name, kinds, offset, maxResults)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: SearchSymbolsRequest)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as SearchSymbolsRequest
        
        if (name != other.name) return false
        if (!(kinds contentDeepEquals other.kinds)) return false
        if (offset != other.offset) return false
        if (maxResults != other.maxResults) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + name.hashCode()
        __r = __r*31 + kinds.contentDeepHashCode()
        __r = __r*31 + offset.hashCode()
        __r = __r*31 + maxResults.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("SearchSymbolsRequest (")
        printer.indent {
            print("name = "); name.print(printer); println()
            print("kinds = "); kinds.print(printer); println()
            print("offset = "); offset.print(printer); println()
            print("maxResults = "); maxResults.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [BathurReSharperMcpToolsetModel.kt:190]
 */
data class SearchSymbolsResponse (
    val status: String,
    val symbols: Array<CppSymbolSummary>,
    val page: CppPageInfo,
    val searchScope: String,
    val diagnostics: Array<CppSemanticDiagnostic>
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(status)
        buffer.writeArray(symbols) { CppSymbolSummary.write(ctx, buffer, it) }
        CppPageInfo.write(ctx, buffer, page)
        buffer.writeString(searchScope)
        buffer.writeArray(diagnostics) { CppSemanticDiagnostic.write(ctx, buffer, it) }
    }
    //companion
    
    companion object : IMarshaller<SearchSymbolsResponse> {
        override val _type: KClass<SearchSymbolsResponse> = SearchSymbolsResponse::class
        override val id: RdId get() = RdId(2260000320573547041)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): SearchSymbolsResponse  {
            val status = buffer.readString()
            val symbols = buffer.readArray {CppSymbolSummary.read(ctx, buffer)}
            val page = CppPageInfo.read(ctx, buffer)
            val searchScope = buffer.readString()
            val diagnostics = buffer.readArray {CppSemanticDiagnostic.read(ctx, buffer)}
            return SearchSymbolsResponse(status, symbols, page, searchScope, diagnostics)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: SearchSymbolsResponse)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as SearchSymbolsResponse
        
        if (status != other.status) return false
        if (!(symbols contentDeepEquals other.symbols)) return false
        if (page != other.page) return false
        if (searchScope != other.searchScope) return false
        if (!(diagnostics contentDeepEquals other.diagnostics)) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + status.hashCode()
        __r = __r*31 + symbols.contentDeepHashCode()
        __r = __r*31 + page.hashCode()
        __r = __r*31 + searchScope.hashCode()
        __r = __r*31 + diagnostics.contentDeepHashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("SearchSymbolsResponse (")
        printer.indent {
            print("status = "); status.print(printer); println()
            print("symbols = "); symbols.print(printer); println()
            print("page = "); page.print(printer); println()
            print("searchScope = "); searchScope.print(printer); println()
            print("diagnostics = "); diagnostics.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}
