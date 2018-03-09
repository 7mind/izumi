package com.github.pshirshov.izumi.idealingua.translator.togolang

import com.github.pshirshov.izumi.idealingua.model.il.{DomainDefinition, Typespace}
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.FinalTranslator
import com.github.pshirshov.izumi.idealingua.translator.toscala.TranslatorExtension

class FinalTranslatorGoLangImpl extends FinalTranslator {

  override def translate(domain: DomainDefinition, extensions: Seq[TranslatorExtension]): Seq[Module] = {
    val typespace = new Typespace(domain)
    typespace.verify()
    new GoLangTranslator(typespace).translate()
  }
}
