package com.github.pshirshov.izumi.idealingua.translator.totypescript

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DomainDefinition
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.typespace.TypespaceImpl
import com.github.pshirshov.izumi.idealingua.translator.totypescript.extensions.TypeScriptTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.{FinalTranslator, TranslatorExtension}

class FinalTranslatorTypeScriptImpl extends FinalTranslator {

  override def translate(domain: DomainDefinition, extensions: Seq[TranslatorExtension]): Seq[Module] = {
    val typespace = new TypespaceImpl(domain)
    typespace.verify()
    val tsExtensions = extensions.collect({ case t: TypeScriptTranslatorExtension => t })
    val modules = new TypeScriptTranslator(typespace, tsExtensions).translate()
    postVerify(modules)
  }

  def verified(domain: DomainDefinition): DomainDefinition = {
    domain
  }

  def postVerify(modules: Seq[Module]): Seq[Module] = {
    modules
  }
}
