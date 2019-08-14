package izumi.idealingua.translator.totypescript

import izumi.idealingua.model.publishing.manifests.TypeScriptBuildManifest
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.translator.totypescript.extensions.{TypeScriptTranslatorExtension, TypeScriptTranslatorExtensions}
import izumi.idealingua.translator.totypescript.tools.ModuleTools
import izumi.idealingua.translator.totypescript.types.TypeScriptTypeConverter

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
