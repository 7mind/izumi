package izumi.idealingua.translator.totypescript

import izumi.idealingua.model.publishing.BuildManifest
import izumi.idealingua.model.publishing.manifests.TypeScriptBuildManifest
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.model.typespace.verification.VerificationRule
import izumi.idealingua.model.typespace.verification.rules.ReservedKeywordRule
import izumi.idealingua.translator.CompilerOptions.TypescriptTranslatorOptions
import izumi.idealingua.translator._
import izumi.idealingua.translator.totypescript.layout.TypescriptLayouter

object TypescriptTranslatorDescriptor extends TranslatorDescriptor[TypescriptTranslatorOptions] {
  override def defaultManifest: BuildManifest = TypeScriptBuildManifest.example

  override def typedOptions(options: UntypedCompilerOptions): TypescriptTranslatorOptions = CompilerOptions.from(options)

  override def language: IDLLanguage = IDLLanguage.Typescript

  override def defaultExtensions: Seq[TranslatorExtension] = TypeScriptTranslator.defaultExtensions

  override def make(typespace: Typespace, options: UntypedCompilerOptions): Translator = new TypeScriptTranslator(typespace, typedOptions(options))

  override def rules: Seq[VerificationRule] = Seq(
    ReservedKeywordRule.warning("typescript", keywords),
  )

  override def makeHook(options: UntypedCompilerOptions): TranslationLayouter = new TypescriptLayouter(typedOptions(options))

  // https://github.com/Microsoft/TypeScript/issues/2536#issuecomment-87194347
  val keywords: Set[String] = Set(
    "break",
    "case",
    "catch",
    "class",
    "const",
    "continue",
    "debugger",
    "default",
    "delete",
    "do",
    "else",
    "enum",
    "export",
    "extends",
    "false",
    "finally",
    "for",
    "function",
    "if",
    "import",
    "in",
    "instanceof",
    "new",
    "null",
    "return",
    "super",
    "switch",
    "this",
    "throw",
    "true",
    "try",
    "typeof",
    "var",
    "void",
    "while",
    "with",
    //
    "as",
    "implements",
    "interface",
    "let",
    "package",
    "private",
    "protected",
    "public",
    "static",
    "yield",
    //
    "any",
    "boolean",
    "constructor",
    "declare",
    "get",
    "module",
    "require",
    "number",
    "set",
    "string",
    "symbol",
    "type",
    "from",
    "of",
  )
}
