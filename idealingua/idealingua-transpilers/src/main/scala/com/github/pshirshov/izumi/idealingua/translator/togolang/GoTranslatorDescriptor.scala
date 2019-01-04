package com.github.pshirshov.izumi.idealingua.translator.togolang

import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.model.typespace.verification.VerificationRule
import com.github.pshirshov.izumi.idealingua.model.typespace.verification.rules.CyclicImportsRule
import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.GoTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator._

object GoTranslatorDescriptor extends TranslatorDescriptor[GoTranslatorOptions] {

  override def typedOptions(options: UntypedCompilerOptions): GoTranslatorOptions = CompilerOptions.from(options)

  override def language: IDLLanguage = IDLLanguage.Go

  override def defaultExtensions: Seq[TranslatorExtension] = GoLangTranslator.defaultExtensions

  override def make(typespace: Typespace, options: UntypedCompilerOptions): Translator = new GoLangTranslator(typespace, typedOptions(options))

  override def rules: Seq[VerificationRule] = Seq(
    CyclicImportsRule.error("Go does not support cyclic imports")
  )

  override def makeHook(options: UntypedCompilerOptions): TranslationLayouter = new GoLayouter(typedOptions(options))
}
