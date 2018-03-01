package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.il.{DomainDefinition, Typespace}
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.FinalTranslator

class FinalTranslatorScalaImpl extends FinalTranslator {

  override def translate(domain: DomainDefinition): Seq[Module] = {
    val typespace = new Typespace(domain)
    typespace.verify()
    val modules = new Translation(typespace).translate()
    postVerify(modules)
  }

  def verified(domain: DomainDefinition): DomainDefinition = {
    domain
  }

  def postVerify(modules: Seq[Module]): Seq[Module] = {
    modules
  }
}
