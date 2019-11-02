package izumi.idealingua.translator.togolang

import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.translator.togolang.extensions.{GoLangTranslatorExtension, GoLangTranslatorExtensions}
import izumi.idealingua.translator.togolang.tools.ModuleTools

class GLTContext(
                  val typespace: Typespace
                  , extensions: Seq[GoLangTranslatorExtension]
                ) {

  final val modules = new ModuleTools()

  final val tools = new GoLangTranslationTools()
  final val ext = {
    new GoLangTranslatorExtensions(this, extensions)
  }
}
