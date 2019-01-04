package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.model.typespace.verification.VerificationRule

trait TranslatorDescriptor[TypedOptions] {
  def language: IDLLanguage
  def defaultExtensions: Seq[TranslatorExtension]
  def typedOptions(options: UntypedCompilerOptions): TypedOptions
  def make(typespace: Typespace, options: UntypedCompilerOptions): Translator
  def makeHook(options: UntypedCompilerOptions): PostTranslationHook
  def rules: Seq[VerificationRule]
}
