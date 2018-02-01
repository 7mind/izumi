package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.finaldef.DomainDefinition
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.FinalTranslator

class FinalTranslatorScalaImpl extends FinalTranslator {

  override def translate(domain: DomainDefinition): Seq[Module] = {
    postVerify(new Translation(verified(domain)).translate())
  }

  def verified(domain: DomainDefinition): DomainDefinition = {
    // TODO
    domain
  }

  def postVerify(modules: Seq[Module]): Seq[Module] = {
    modules
  }
}
