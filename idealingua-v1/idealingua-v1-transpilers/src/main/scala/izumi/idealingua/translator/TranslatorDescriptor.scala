package izumi.idealingua.translator

import izumi.idealingua.model.publishing.BuildManifest
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.model.typespace.verification.VerificationRule

trait TranslatorDescriptor[TypedOptions] {
  def language: IDLLanguage
  def defaultExtensions: Seq[TranslatorExtension]
  def defaultManifest: BuildManifest
  def typedOptions(options: UntypedCompilerOptions): TypedOptions
  def make(typespace: Typespace, options: UntypedCompilerOptions): Translator
  def makeHook(options: UntypedCompilerOptions): TranslationLayouter
  def rules: Seq[VerificationRule]
}
