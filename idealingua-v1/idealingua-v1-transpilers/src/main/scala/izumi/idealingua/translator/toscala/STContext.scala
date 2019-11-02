package izumi.idealingua.translator.toscala

import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.translator.toscala.extensions.{ScalaTranslatorExtension, ScalaTranslatorExtensions}
import izumi.idealingua.translator.toscala.tools.{ModuleTools, ScalaTranslationTools}
import izumi.idealingua.translator.toscala.types.ScalaTypeConverter
import izumi.idealingua.translator.toscala.types.runtime.IDLRuntimeTypes

class STContext(
                 val typespace: Typespace
                 , extensions: Seq[ScalaTranslatorExtension]
               ) {
  final val conv = new ScalaTypeConverter(typespace.domain.id)
  final val rt: IDLRuntimeTypes.type = IDLRuntimeTypes

  final val modules = new ModuleTools()

  final val tools = new ScalaTranslationTools(this)
  final val ext = {
    new ScalaTranslatorExtensions(this, extensions)
  }

  final val compositeRenderer = new CompositeRenderer(this)
  final val adtRenderer = new AdtRenderer(this)
  final val interfaceRenderer = new InterfaceRenderer(this)
  final val idRenderer = new IdRenderer(this)
  final val serviceRenderer = new ServiceRenderer(this)
  final val enumRenderer = new EnumRenderer(this)
}
