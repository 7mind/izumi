package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.ScalaTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.{FinalTranslator, TranslatorExtension}

class FinalTranslatorScalaImpl extends FinalTranslator {

  override def translate(typespace: Typespace, extensions: Seq[TranslatorExtension]): Seq[Module] = {
    val scalaExtensions = extensions.collect({ case t: ScalaTranslatorExtension => t })
    val modules = new ScalaTranslator(typespace, scalaExtensions).translate()
    postVerify(modules)
  }

  def postVerify(modules: Seq[Module]): Seq[Module] = {
    // TODO: some sanity checks here?..
    modules
  }
}
