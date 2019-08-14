package com.github.pshirshov.izumi.idealingua.translator.tocsharp

import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions.{CSharpTranslatorExtension, CSharpTranslatorExtensions}
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.tools.ModuleTools

class CSTContext(
                  val typespace: Typespace
                  , extensions: Seq[CSharpTranslatorExtension]
                ) {

  final val modules = new ModuleTools()

  final val tools = new CSharpTranslationTools()
  final val ext = {
    new CSharpTranslatorExtensions(this, extensions)
  }
}
