package com.github.pshirshov.izumi.idealingua.translator.togolang

import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.GoLangBuildManifest
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.model.typespace.verification.VerificationRule
import com.github.pshirshov.izumi.idealingua.model.typespace.verification.rules.{CyclicImportsRule, ReservedKeywordRule}
import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.GoTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator._

object GoTranslatorDescriptor extends TranslatorDescriptor[GoTranslatorOptions] {
  override def defaultManifest: BuildManifest = GoLangBuildManifest.default

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
