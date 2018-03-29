package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.il.ast.DomainDefinition
import com.github.pshirshov.izumi.idealingua.model.output.Module

trait FinalTranslator {
  def translate(domain: DomainDefinition, extensions: Seq[TranslatorExtension]): Seq[Module]
}
