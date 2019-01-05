package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.model.typespace.verification.VerificationRule
import com.github.pshirshov.izumi.idealingua.model.typespace.verification.rules.ReservedKeywordRule
import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.ScalaTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator._
import com.github.pshirshov.izumi.idealingua.translator.toscala.layout.ScalaLayouter

object ScalaTranslatorDescriptor extends TranslatorDescriptor[ScalaTranslatorOptions] {

  override def typedOptions(options: UntypedCompilerOptions): ScalaTranslatorOptions = CompilerOptions.from(options)

  override def language: IDLLanguage = IDLLanguage.Scala

  override def defaultExtensions: Seq[TranslatorExtension] = ScalaTranslator.defaultExtensions

  override def make(typespace: Typespace, options: UntypedCompilerOptions): Translator = new ScalaTranslator(typespace, typedOptions(options))

  override def rules: Seq[VerificationRule] = Seq(
    ReservedKeywordRule.warning("scala", keywords),
  )

  override def makeHook(options: UntypedCompilerOptions): TranslationLayouter = new ScalaLayouter(typedOptions(options))

  // https://scala-lang.org/files/archive/spec/2.12/01-lexical-syntax.html
  val keywords: Set[String] = Set(
    "abstract",
    "case",
    "catch",
    "class",
    "def",
    "do",
    "else",
    "extends",
    "false",
    "final",
    "finally",
    "for",
    "forSome",
    "if",
    "implicit",
    "import",
    "lazy",
    "macro",
    "match",
    "new",
    "null",
    "object",
    "override",
    "package",
    "private",
    "protected",
    "return",
    "sealed",
    "super",
    "this",
    "throw",
    "trait",
    "try",
    "true",
    "type",
    "val",
    "var",
    "while",
    "with",
    "yield",
  )
}
