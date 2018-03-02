package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.il.DomainDefinition
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslatorExtension

trait FinalTranslator {
  def translate(domain: DomainDefinition, extensions: Seq[ScalaTranslatorExtension]): Seq[Module]
}
