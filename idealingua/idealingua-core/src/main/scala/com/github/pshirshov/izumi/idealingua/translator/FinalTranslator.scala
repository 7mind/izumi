package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace

trait FinalTranslator {
  def translate(typespace: Typespace, extensions: Seq[TranslatorExtension])(implicit manifest: Option[BuildManifest]): Seq[Module]
}
