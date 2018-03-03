package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.JavaType
import com.github.pshirshov.izumi.idealingua.model.common.Indefinite
import com.github.pshirshov.izumi.idealingua.model.il.serialization.ILSchemaSerializerJson4sImpl
import com.github.pshirshov.izumi.idealingua.model.il.{TypeSignature, Typespace}

class ScalaTranslationContext(
                               val typespace: Typespace
                             ) {
  final val conv = new ScalaTypeConverter(typespace.domain.id)
  final val rt: IDLRuntimeTypes.type = IDLRuntimeTypes
  final val sig = new TypeSignature(typespace)
  final val schemaSerializer: ILSchemaSerializerJson4sImpl.type = ILSchemaSerializerJson4sImpl
  final val domainsDomain = Indefinite(Seq("izumi", "idealingua", "domains"), typespace.domain.id.id.capitalize)
  final val tDomain = conv.toScala(JavaType(domainsDomain))

}
