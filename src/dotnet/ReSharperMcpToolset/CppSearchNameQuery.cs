// Copyright (C) 2026 Bathur.
// Licensed under GPL-3.0-only with the Rider/ReSharper host linking permission.
// See LICENSE and LICENSING.md in the public source root.

using System;
using System.Linq;
using JetBrains.ReSharper.Psi.Cpp;
using JetBrains.ReSharper.Psi.Cpp.Caches;
using JetBrains.ReSharper.Psi.Cpp.Parsing;
using JetBrains.ReSharper.Psi.Cpp.Symbols;
using JetBrains.ReSharper.Psi.Cpp.Tree;
using JetBrains.ReSharper.Psi.Tree;

namespace Bathur.ReSharperMcpToolset
{
    internal sealed class CppSearchNameQuery
    {
        private const string ConversionMarker = "<conversion>";
        private readonly string _declaredShortName;
        private readonly string _qualifiedPresentation;
        private readonly string _simplePresentation;
        private readonly bool _isQualified;

        private CppSearchNameQuery(
            string[] indexKeys,
            string declaredShortName,
            string qualifiedPresentation,
            string simplePresentation,
            bool isQualified,
            bool requiresQualifiedName)
        {
            IndexKeys = indexKeys;
            _declaredShortName = declaredShortName;
            _qualifiedPresentation = qualifiedPresentation;
            _simplePresentation = simplePresentation;
            _isQualified = isQualified;
            RequiresQualifiedName = requiresQualifiedName;
        }

        public string IndexKey => IndexKeys[0];
        public string[] IndexKeys { get; }
        public bool RequiresQualifiedName { get; }

        public bool Matches(string shortName, string qualifiedName)
        {
            if (!string.Equals(shortName, _declaredShortName, StringComparison.Ordinal))
                return false;
            if (!RequiresQualifiedName)
                return true;
            if (string.IsNullOrEmpty(qualifiedName))
                return false;

            var candidate = TrimGlobalPrefix(qualifiedName);
            return _isQualified
                ? string.Equals(candidate, _qualifiedPresentation, StringComparison.Ordinal)
                : string.Equals(candidate, _simplePresentation, StringComparison.Ordinal) ||
                  candidate.EndsWith("::" + _simplePresentation, StringComparison.Ordinal);
        }

        internal static bool TryCreate(
            CppGlobalSymbolCache cache,
            string query,
            out CppSearchNameQuery parsed,
            out string error)
        {
            parsed = null;
            error = null;
            if (string.IsNullOrWhiteSpace(query))
            {
                error = "name must not be empty";
                return false;
            }

            query = query.Trim();
            if (TryCreateCompatibility(query, out parsed))
                return true;

            var sourceName = query;
            if (FindReportedConversionMarker(query) >= 0)
            {
                if (!TryRewriteReportedConversion(query, out sourceName, out error))
                    return false;
            }
            else
            {
                string rewrittenLiteral;
                if (TryRewriteReportedLiteral(query, out rewrittenLiteral))
                    sourceName = rewrittenLiteral;
            }

            var stage = "resolve C++ module";
            try
            {
                var module = cache?.CppModule;
                if (module == null)
                {
                    throw new InvalidOperationException("The current C++ module is unavailable for name parsing.");
                }

                stage = "create C++ element factory";
                var factory = CppElementFactory.GetInstance(module);
                stage = "create parsing context";
                // Name learning visits the containing CppFile for named template arguments.
                // A parsed reference uses sibling context, so anchor it below an in-memory
                // file rather than on the file root or an unattached code fragment.
                var contextFile = factory.CreateFile("int __resharper_mcp_name_context;");
                var context = contextFile.Children().OfType<SimpleDeclaration>().FirstOrDefault();
                if (context == null)
                    throw new InvalidOperationException("The in-memory C++ name context has no declaration anchor.");
                stage = "parse qualified reference";
                var reference = factory.CreateReferenceWithoutPreprocessing<QualifiedReference>(
                    context, sourceName, CppCompositeNodeTypes.QUALIFIED_REFERENCE);
                stage = "validate reference syntax";
                if (reference == null)
                {
                    error = "name must be a complete supported C++ symbol name";
                    return false;
                }

                ITreeNode parsedRoot = reference;
                while (parsedRoot.Parent != null) parsedRoot = parsedRoot.Parent;
                if (parsedRoot.ContainsErrorElement() ||
                    !string.Equals(reference.GetText().Trim(), sourceName, StringComparison.Ordinal))
                {
                    error = "name must be a complete supported C++ symbol name";
                    return false;
                }

                stage = "derive qualified name";
                var name = reference.GetQualifiedName();
                if (!name.IsValid())
                {
                    error = "The C++ name parser did not produce a valid symbol name.";
                    return false;
                }

                stage = "build exact-name query";
                parsed = FromParsedName(query, name);
                return true;
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception exception)
            {
                throw new InvalidOperationException(
                    $"Rider C++ search-name preparation failed during {stage}.", exception);
            }
        }

        internal static CppSearchNameQuery FromParsedName(string originalQuery, CppQualifiedName name)
        {
            name = CppQualifiedNameUtil.DropGlobalNamespace(name);
            if (!name.IsValid()) throw new ArgumentException("A parsed symbol name is required.", nameof(name));
            var part = name.Name;
            var indexKey = CppQualifiedNameUtil.GetShortName(part);
            var declaredShortName = CppQualifiedNameUtil.GetShortNameForDeclaredElement(part);
            if (string.IsNullOrWhiteSpace(indexKey) || string.IsNullOrWhiteSpace(declaredShortName))
                throw new ArgumentException("The parsed name has no symbol-index key.", nameof(name));

            var isQualified = name.HasQualifier();
            var qualifiedPresentation = name.ToString();
            var simplePresentation = part.ToString();
            string ignoredSource;
            string ignoredError;
            if (TryRewriteReportedConversion(originalQuery, out ignoredSource, out ignoredError))
            {
                // Rider's returned conversion descriptor is opaque. The caller parsed a
                // neutral conversion only to validate its owner; match the original text.
                qualifiedPresentation = TrimGlobalPrefix(originalQuery.Trim());
                simplePresentation = qualifiedPresentation.Substring(
                    FindReportedConversionMarker(qualifiedPresentation));
            }

            var keys = new[] { indexKey };
            if (!isQualified && CppQualifiedNamePartUtil.IsQualifiedId(part))
            {
                // A literal's declared short name is its suffix. Preserve ordinary
                // identifier matches and discover literal matches with one additional key.
                CppQualifiedNamePart literalPart = new CppUserDefinedLiteralId(declaredShortName);
                keys = new[] { indexKey, CppQualifiedNameUtil.GetShortName(literalPart) }
                    .Distinct(StringComparer.Ordinal).ToArray();
            }

            return new CppSearchNameQuery(
                keys, declaredShortName, qualifiedPresentation, simplePresentation, isQualified,
                isQualified || !string.Equals(simplePresentation, declaredShortName, StringComparison.Ordinal));
        }

        internal static bool TryCreateCompatibility(string query, out CppSearchNameQuery parsed)
        {
            parsed = null;
            var normalized = TrimGlobalPrefix(query?.Trim());
            if (string.IsNullOrEmpty(normalized))
                return false;

            // Preserve ordinary identifier queries without requiring a PSI fragment.
            // Names with template/operator syntax continue through the SDK parser.
            var parts = normalized.Split(new[] { "::" }, StringSplitOptions.None);
            if (parts.Any(part => part.Length == 0 ||
                                  !(char.IsLetter(part[0]) || part[0] == '_') ||
                                  part.Skip(1).Any(character => !(char.IsLetterOrDigit(character) || character == '_'))))
                return false;

            var shortName = parts[parts.Length - 1];
            var isQualified = parts.Length > 1;
            var keys = new[] { shortName };
            if (!isQualified && shortName != "operator")
            {
                CppQualifiedNamePart literalPart = new CppUserDefinedLiteralId(shortName);
                keys = new[] { shortName, CppQualifiedNameUtil.GetShortName(literalPart) }
                    .Distinct(StringComparer.Ordinal).ToArray();
            }

            parsed = new CppSearchNameQuery(keys, shortName, normalized, shortName, isQualified, isQualified);
            return true;
        }

        internal static bool TryRewriteReportedConversion(string query, out string sourceName, out string error)
        {
            sourceName = null;
            error = null;
            if (query == null) return false;
            query = query.Trim();
            var marker = FindReportedConversionMarker(query);
            if (marker < 0) return false;
            var descriptorStart = marker + ConversionMarker.Length;
            if ((marker > 0 && (marker < 2 || query.Substring(marker - 2, 2) != "::")) ||
                descriptorStart == query.Length ||
                query.IndexOf(ConversionMarker, descriptorStart, StringComparison.Ordinal) == descriptorStart ||
                query.Any(character => char.IsControl(character) || character == ';' || character == '{' || character == '}') ||
                char.IsWhiteSpace(query[descriptorStart]) || query[descriptorStart] == ':')
            {
                error = "The returned conversion name has an empty or malformed descriptor.";
                return false;
            }

            // Do not decode the SDK's debug type grammar. Full owner validation is
            // delegated to the SDK parser; the descriptor is used only for exact matching.
            sourceName = query.Substring(0, marker) + "operator bool";
            return true;
        }

        internal static bool TryRewriteReportedLiteral(string query, out string sourceName)
        {
            sourceName = null;
            if (string.IsNullOrEmpty(query)) return false;
            var separator = query.LastIndexOf("::\"\"", StringComparison.Ordinal);
            var start = separator >= 0 ? separator + 2 : 0;
            if (query.Length <= start + 2 || !query.Substring(start).StartsWith("\"\"", StringComparison.Ordinal))
                return false;
            // The SDK validates the suffix and full consumption after this presentation fix.
            sourceName = query.Substring(0, start) + "operator " + query.Substring(start);
            return true;
        }

        private static int FindReportedConversionMarker(string value)
        {
            if (string.IsNullOrEmpty(value)) return -1;
            if (value.StartsWith(ConversionMarker, StringComparison.Ordinal)) return 0;
            var separator = value.LastIndexOf("::" + ConversionMarker, StringComparison.Ordinal);
            return separator >= 0 ? separator + 2 : -1;
        }

        private static string TrimGlobalPrefix(string value)
        {
            return value != null && value.StartsWith("::", StringComparison.Ordinal) ? value.Substring(2) : value;
        }
    }
}
