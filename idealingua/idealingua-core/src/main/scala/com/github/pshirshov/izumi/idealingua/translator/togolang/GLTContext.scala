package com.github.pshirshov.izumi.idealingua.translator.togolang

import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.togolang.extensions.{GoLangTranslatorExtension, GoLangTranslatorExtensions}
import com.github.pshirshov.izumi.idealingua.translator.togolang.tools.ModuleTools

class GLTContext(
                  val typespace: Typespace
                  , extensions: Seq[GoLangTranslatorExtension]
                ) {

  final val modules = new ModuleTools()

  final val tools = new GoLangTranslationTools(this)
  final val ext = {
    new GoLangTranslatorExtensions(this, extensions)
  }
}
