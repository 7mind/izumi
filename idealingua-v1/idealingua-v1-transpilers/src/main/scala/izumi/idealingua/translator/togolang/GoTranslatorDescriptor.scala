package izumi.idealingua.translator.togolang

import izumi.idealingua.model.publishing.BuildManifest
import izumi.idealingua.model.publishing.manifests.GoLangBuildManifest
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.model.typespace.verification.VerificationRule
import izumi.idealingua.model.typespace.verification.rules.{CyclicImportsRule, ReservedKeywordRule}
import izumi.idealingua.translator.CompilerOptions.GoTranslatorOptions
import izumi.idealingua.translator._

object GoTranslatorDescriptor extends TranslatorDescriptor[GoTranslatorOptions] {
  override def defaultManifest: BuildManifest = GoLangBuildManifest.example

  override def typedOptions(options: UntypedCompilerOptions): GoTranslatorOptions = CompilerOptions.from(options)

  override def language: IDLLanguage = IDLLanguage.Go

  override def defaultExtensions: Seq[TranslatorExtension] = GoLangTranslator.defaultExtensions

  override def make(typespace: Typespace, options: UntypedCompilerOptions): Translator = new GoLangTranslator(typespace, typedOptions(options))

  override def rules: Seq[VerificationRule] = Seq(
    CyclicImportsRule.error("Go does not support cyclic imports"),
    ReservedKeywordRule.warning("go", keywords),
  )

  override def makeHook(options: UntypedCompilerOptions): TranslationLayouter = new GoLayouter(typedOptions(options))

  // https://golang.org/ref/spec#Keywords
  val keywords: Set[String] = Set(
    "break",
    "default",
    "func",
    "interface",
    "select",
    "case",
    "defer",
    "go",
    "map",
    "struct",
    "chan",
    "else",
    "goto",
    "package",
    "switch",
    "const",
    "fallthrough",
    "if",
    "range",
    "type",
    "continue",
    "for",
    "import",
    "return",
    "var",
  )
}
