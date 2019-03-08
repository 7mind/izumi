package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2

case class Translated(typespace: Typespace2, modules: Seq[Module])

trait Translator {
  def translate(): Translated
}


