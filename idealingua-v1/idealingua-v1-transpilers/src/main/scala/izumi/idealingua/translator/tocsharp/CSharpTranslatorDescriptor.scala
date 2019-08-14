package izumi.idealingua.translator.tocsharp

import izumi.idealingua.model.publishing.BuildManifest
import izumi.idealingua.model.publishing.manifests.CSharpBuildManifest
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.model.typespace.verification.VerificationRule
import izumi.idealingua.model.typespace.verification.rules.ReservedKeywordRule
import izumi.idealingua.translator.CompilerOptions.CSharpTranslatorOptions
import izumi.idealingua.translator._
import izumi.idealingua.translator.tocsharp.layout.CSharpLayouter

object CSharpTranslatorDescriptor extends TranslatorDescriptor[CSharpTranslatorOptions] {

  override def defaultManifest: BuildManifest = CSharpBuildManifest.example

  override def typedOptions(options: UntypedCompilerOptions): CSharpTranslatorOptions = CompilerOptions.from(options)

  override def language: IDLLanguage = IDLLanguage.CSharp

  override def defaultExtensions: Seq[TranslatorExtension] = CSharpTranslator.defaultExtensions

  override def make(typespace: Typespace, options: UntypedCompilerOptions): Translator = new CSharpTranslator(typespace, typedOptions(options))

  override def rules: Seq[VerificationRule] = Seq(
    ReservedKeywordRule.warning("c#", keywords),
  )

  override def makeHook(options: UntypedCompilerOptions): TranslationLayouter = new CSharpLayouter(typedOptions(options))

  // https://docs.microsoft.com/en-us/dotnet/csharp/language-reference/keywords/
  val keywords: Set[String] = Set(
    "abstract",
    "as",
    "base",
    "bool",
    "break",
    "byte",
    "case",
    "catch",
    "char",
    "checked",
    "class",
    "const",
    "continue",
    "decimal",
    "default",
    "delegate",
    "do",
    "double",
    "else",
    "enum",
    "event",
    "explicit",
    "extern",
    "false",
    "finally",
    "fixed",
    "float",
    "for",
    "foreach",
    "goto",
    "if",
    "implicit",
    "in",
    "int",
    "interface",
    "internal",
    "is",
    "lock",
    "long",
    "namespace",
    "new",
    "null",
    "object",
    "operator",
    "out",
    "override",
    "params",
    "private",
    "protected",
    "public",
    "readonly",
    "ref",
    "return",
    "sbyte",
    "sealed",
    "short",
    "sizeof",
    "stackalloc",
    "static",
    "string",
    "struct",
    "switch",
    "this",
    "throw",
    "true",
    "try",
    "typeof",
    "uint",
    "ulong",
    "unchecked",
    "unsafe",
    "ushort",
    "using",
    "using",
    "static",
    "virtual",
    "void",
    "volatile",
    "while",
  )

//  val contextualKeywords: Set[String] = Set(
//    "add",
//    "alias",
//    "ascending",
//    "async",
//    "await",
//    "by",
//    "descending",
//    "dynamic",
//    "equals",
//    "from",
//    "get",
//    "global",
//    "group",
//    "into",
//    "join",
//    "let",
//    "nameof",
//    "on",
//    "orderby",
//    "partial",
//    "remove",
//    "select",
//    "set",
//    "value",
//    "var",
//    "when",
//    "where",
//    "yield",
//  )
}

