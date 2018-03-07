package com.github.pshirshov.izumi.idealingua.translator.tots

import com.github.pshirshov.izumi.idealingua.model.il.{DomainDefinition, Typespace}
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.FinalTranslator
import com.github.pshirshov.izumi.idealingua.translator.toscala.TranslatorExtension

class FinalTranslatorTSImpl extends FinalTranslator {

  override def translate(domain: DomainDefinition, extensions: Seq[TranslatorExtension]): Seq[Module] = {
    val typespace = new Typespace(domain)
    typespace.verify()
    val modules = new TypeScriptTranslator(typespace).translate()
    postVerify(modules)
  }

  def verified(domain: DomainDefinition): DomainDefinition = {
    domain
  }

  def postVerify(modules: Seq[Module]): Seq[Module] = {
    modules
  }
}

