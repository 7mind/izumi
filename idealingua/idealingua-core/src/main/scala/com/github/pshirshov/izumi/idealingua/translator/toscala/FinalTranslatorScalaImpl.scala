package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DomainDefinition
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.{FinalTranslator, TranslatorExtension}
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.ScalaTranslatorExtension

class FinalTranslatorScalaImpl extends FinalTranslator {

  override def translate(domain: DomainDefinition, extensions: Seq[TranslatorExtension]): Seq[Module] = {
    val typespace = new Typespace(domain)
    typespace.verify()
    val scalaExtensions = extensions.collect({ case t: ScalaTranslatorExtension => t })
    val modules = new ScalaTranslator(typespace, scalaExtensions).translate()
    postVerify(modules)
  }

  def verified(domain: DomainDefinition): DomainDefinition = {
    domain
  }

  def postVerify(modules: Seq[Module]): Seq[Module] = {
    modules
  }
}
