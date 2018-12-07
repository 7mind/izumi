package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.model.typespace.verification.VerificationRule

trait TranslatorDescriptor {
  def language: IDLLanguage
  def defaultExtensions: Seq[TranslatorExtension]
  def make(typespace: Typespace, options: UntypedCompilerOptions): Translator
  def rules: Seq[VerificationRule]
}
