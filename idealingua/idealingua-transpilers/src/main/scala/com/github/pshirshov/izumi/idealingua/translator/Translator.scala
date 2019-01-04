package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace

case class Translated(typespace: Typespace, modules: Seq[Module])

trait Translator {
  def translate(): Translated
}


