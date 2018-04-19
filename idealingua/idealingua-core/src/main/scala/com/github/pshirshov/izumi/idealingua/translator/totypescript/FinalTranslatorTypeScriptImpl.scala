package com.github.pshirshov.izumi.idealingua.translator.totypescript

import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.totypescript.extensions.TypeScriptTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.{FinalTranslator, TranslatorExtension}

class FinalTranslatorTypeScriptImpl extends FinalTranslator {

  override def translate(typespace: Typespace, extensions: Seq[TranslatorExtension]): Seq[Module] = {
    val tsExtensions = extensions.collect({ case t: TypeScriptTranslatorExtension => t })
    val modules = new TypeScriptTranslator(typespace, tsExtensions).translate()
    postVerify(modules)
  }


  def postVerify(modules: Seq[Module]): Seq[Module] = {
    // TODO: some sanity checks here?..
    modules
  }
}
