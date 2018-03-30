package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.il.serialization.ILSchemaSerializerJson4sImpl
import com.github.pshirshov.izumi.idealingua.model.il.Typespace
import com.github.pshirshov.izumi.idealingua.model.il.extensions.TypeSignature
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.{ScalaTranslatorExtension, ScalaTranslatorExtensions}
import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ModuleTools
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.ScalaTypeConverter
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.runtime.IDLRuntimeTypes

class STContext(
                               val typespace: Typespace
                               , extensions: Seq[ScalaTranslatorExtension]
                             ) {
  final val conv = new ScalaTypeConverter(typespace.domain.id)
  final val rt: IDLRuntimeTypes.type = IDLRuntimeTypes
  final val sig = new TypeSignature(typespace)
  final val schemaSerializer: ILSchemaSerializerJson4sImpl.type = ILSchemaSerializerJson4sImpl

  final val modules = new ModuleTools(rt)

  final val tools = new ScalaTranslationTools(this)
  final val ext = {
    new ScalaTranslatorExtensions(this, extensions)
  }
}
