package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace

trait FinalTranslator {
  def translate(typespace: Typespace, extensions: Seq[TranslatorExtension]): Seq[Module]
}
