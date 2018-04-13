package com.github.pshirshov.izumi.idealingua.translator.totypescript

import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.tools.extensions.TypeSignature
import com.github.pshirshov.izumi.idealingua.translator.totypescript.extensions.{TypeScriptTranslatorExtension, TypeScriptTranslatorExtensions}
import com.github.pshirshov.izumi.idealingua.translator.totypescript.tools.ModuleTools
import com.github.pshirshov.izumi.idealingua.translator.totypescript.types.TypeScriptTypeConverter

class TSTContext(
                 val typespace: Typespace
                 , extensions: Seq[TypeScriptTranslatorExtension]
               ) {
  final val conv = new TypeScriptTypeConverter(typespace.domain.id)
  final val sig = new TypeSignature(typespace)

  final val modules = new ModuleTools()

  final val tools = new TypeScriptTranslationTools(this)
  final val ext = {
    new TypeScriptTranslatorExtensions(this, extensions)
  }
}
