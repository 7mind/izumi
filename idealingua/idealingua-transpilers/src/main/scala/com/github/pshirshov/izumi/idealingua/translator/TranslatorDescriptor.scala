package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2

trait TranslatorDescriptor {
  def language: IDLLanguage
  def defaultManifest: BuildManifest

  protected type TypedOptions

  protected def transform(options: UntypedCompilerOptions): TypedOptions
  //protected def defaultExtensions: Seq[TranslatorExtension]

  def translate(options: UntypedCompilerOptions, ts: Typespace2): Translated
  def layout(outputs: Seq[Translated]): Layouted
}
