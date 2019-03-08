package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2

trait TranslatorDescriptor[TypedOptions] {
  def language: IDLLanguage
  def defaultExtensions: Seq[TranslatorExtension]
  def defaultManifest: BuildManifest
  def typedOptions(options: UntypedCompilerOptions): TypedOptions
  def make(typespace: Typespace2, options: UntypedCompilerOptions): Translator
  def makeHook(options: UntypedCompilerOptions): TranslationLayouter
  //def rules: Seq[VerificationRule]
}
