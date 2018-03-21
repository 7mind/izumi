package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.il.serialization.ILSchemaSerializerJson4sImpl
import com.github.pshirshov.izumi.idealingua.model.il.{TypeSignature, Typespace}

class ScalaTranslationContext(
                               val typespace: Typespace
                             ) {
  final val conv = new ScalaTypeConverter(typespace.domain.id)
  final val rt: IDLRuntimeTypes.type = IDLRuntimeTypes
  final val sig = new TypeSignature(typespace)
  final val schemaSerializer: ILSchemaSerializerJson4sImpl.type = ILSchemaSerializerJson4sImpl

  final val domainsDomain = conv.domainCompanionId(typespace.domain)
  final val tDomain = conv.toScala(domainsDomain)

  final val modules = new ModuleTools(rt)
}
