package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._

case class ILParsedId(pkg: Seq[String], name: String) {
  def toEnumId: EnumId = EnumId(pkg, name)

  def toAliasId: AliasId = AliasId(pkg, name)

  def toIdId: IdentifierId = IdentifierId(pkg, name)

  def toMixinId: InterfaceId = InterfaceId(pkg, name)

  def toDataId: DTOId = DTOId(pkg, name)

  def toAdtId: AdtId = AdtId(pkg, name)

  def toTypeId: AbstractTypeId = {
    IndefiniteId(pkg, name)
  }

  def toServiceId: ServiceId = ServiceId(pkg, name)

  def toGeneric(params: Seq[Seq[AbstractTypeId]]): AbstractTypeId = {
    if (params.nonEmpty) {
      IndefiniteGeneric(pkg, name, params.flatten.toList)
    } else {
      toTypeId
    }
  }
}

object ILParsedId {
  def apply(name: String): ILParsedId = new ILParsedId(Seq.empty, name)
}
