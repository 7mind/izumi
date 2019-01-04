package com.github.pshirshov.izumi.idealingua.translator.tocsharp

import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.model.typespace.verification.VerificationRule
import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.CSharpTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator._

object CSharpTranslatorDescriptor extends TranslatorDescriptor[CSharpTranslatorOptions] {

  override def typedOptions(options: UntypedCompilerOptions): CSharpTranslatorOptions = CompilerOptions.from(options)

  override def language: IDLLanguage = IDLLanguage.CSharp

  override def defaultExtensions: Seq[TranslatorExtension] = CSharpTranslator.defaultExtensions

  override def make(typespace: Typespace, options: UntypedCompilerOptions): Translator = new CSharpTranslator(typespace, typedOptions(options))

  override def rules: Seq[VerificationRule] = Seq.empty

  override def makeHook(options: UntypedCompilerOptions): TranslationLayouter = new CSharpLayouter(typedOptions(options))
}
