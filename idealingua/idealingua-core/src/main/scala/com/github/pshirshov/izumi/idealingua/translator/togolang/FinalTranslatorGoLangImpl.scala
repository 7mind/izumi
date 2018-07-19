package com.github.pshirshov.izumi.idealingua.translator.togolang

import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.togolang.extensions.GoLangTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.{FinalTranslator, TranslatorExtension}

class FinalTranslatorGoLangImpl extends FinalTranslator {

  override def translate(typespace: Typespace, extensions: Seq[TranslatorExtension])(implicit manifest: Option[BuildManifest]): Seq[Module] = {
    val glExtensions = extensions.collect({ case t: GoLangTranslatorExtension => t })
    val modules = new GoLangTranslator(typespace, glExtensions).translate()
    postVerify(modules)
  }

  def postVerify(modules: Seq[Module]): Seq[Module] = {
    modules
  }
}
