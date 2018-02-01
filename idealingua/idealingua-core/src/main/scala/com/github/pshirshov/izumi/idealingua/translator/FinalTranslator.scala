package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.finaldef.DomainDefinition
import com.github.pshirshov.izumi.idealingua.model.output.Module

trait FinalTranslator {
  def translate(domain: DomainDefinition): Seq[Module]
}
