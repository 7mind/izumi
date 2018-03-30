package com.github.pshirshov.izumi.idealingua.translator.togolang

import com.github.pshirshov.izumi.idealingua.model.il.Typespace
import com.github.pshirshov.izumi.idealingua.model.il.ast.DomainDefinition
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.{FinalTranslator, TranslatorExtension}

class FinalTranslatorGoLangImpl extends FinalTranslator {

  override def translate(domain: DomainDefinition, extensions: Seq[TranslatorExtension]): Seq[Module] = {
    val typespace = new Typespace(domain)
    typespace.verify()
    new GoLangTranslator(typespace).translate()
  }
}
