package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.il.DomainDefinition
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.toscala.TranslatorExtension

trait FinalTranslator {
  def translate(domain: DomainDefinition, extensions: Seq[TranslatorExtension]): Seq[Module]
}
