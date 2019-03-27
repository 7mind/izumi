package com.github.pshirshov.izumi.idealingua.translator.totypescript

import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.TypeScriptBuildManifest
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.totypescript.extensions.{TypeScriptTranslatorExtension, TypeScriptTranslatorExtensions}
import com.github.pshirshov.izumi.idealingua.translator.totypescript.tools.ModuleTools
import com.github.pshirshov.izumi.idealingua.translator.totypescript.types.TypeScriptTypeConverter

class TSTContext(
                 val typespace: Typespace,
                  val manifest: TypeScriptBuildManifest
                 , extensions: Seq[TypeScriptTranslatorExtension]
               ) {
  final val conv = new TypeScriptTypeConverter()

  final val modules = new ModuleTools()

  final val tools = new TypeScriptTranslationTools()
  final val ext = {
    new TypeScriptTranslatorExtensions(this, extensions)
  }
}
