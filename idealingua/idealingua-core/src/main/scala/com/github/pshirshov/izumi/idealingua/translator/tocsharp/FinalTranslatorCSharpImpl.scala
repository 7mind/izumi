package com.github.pshirshov.izumi.idealingua.translator.tocsharp

import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions.CSharpTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.{FinalTranslator, TranslatorExtension}

class FinalTranslatorCSharpImpl extends FinalTranslator {

  override def translate(typespace: Typespace, extensions: Seq[TranslatorExtension])(implicit manifest: Option[BuildManifest]): Seq[Module] = {
    val csExtensions = extensions.collect({ case t: CSharpTranslatorExtension => t })
    val modules = new CSharpTranslator(typespace, csExtensions).translate()
    postVerify(modules)
  }

  def postVerify(modules: Seq[Module]): Seq[Module] = {
    modules
  }
}
