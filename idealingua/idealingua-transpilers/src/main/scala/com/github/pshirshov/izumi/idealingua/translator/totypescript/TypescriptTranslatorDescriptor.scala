package com.github.pshirshov.izumi.idealingua.translator.totypescript

import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.model.typespace.verification.VerificationRule
import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.TypescriptTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator._

object TypescriptTranslatorDescriptor extends TranslatorDescriptor[TypescriptTranslatorOptions] {

  override def typedOptions(options: UntypedCompilerOptions): TypescriptTranslatorOptions = CompilerOptions.from(options)

  override def language: IDLLanguage = IDLLanguage.Typescript

  override def defaultExtensions: Seq[TranslatorExtension] = TypeScriptTranslator.defaultExtensions

  override def make(typespace: Typespace, options: UntypedCompilerOptions): Translator = new TypeScriptTranslator(typespace, typedOptions(options))

  override def rules: Seq[VerificationRule] = Seq.empty

  override def makeHook(options: UntypedCompilerOptions): TranslationLayouter = new TypescriptLayouter(typedOptions(options))
}
