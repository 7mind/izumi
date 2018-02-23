package com.github.pshirshov.izumi.idealingua.il

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{TypeId, UserType}
import com.github.pshirshov.izumi.idealingua.model.il.DomainId

case class AbstractId(pkg: Seq[String], id: String) {
  def toDomainId: DomainId = DomainId(pkg, id)

  def toEnumId: EnumId = EnumId(pkg, id)

  def toAliasId: AliasId = AliasId(pkg, id)

  def toIdId: IdentifierId = IdentifierId(pkg, id)

  def toMixinId: InterfaceId = InterfaceId(pkg, id)

  def toDataId: DTOId = DTOId(pkg, id)

  def toTypeId: TypeId = UserType(pkg, id)

  def toServiceId: ServiceId = ServiceId(pkg, id)
}

object AbstractId {
  def apply(name: String): AbstractId = new AbstractId(Seq.empty, name)

  def apply(pkg: Seq[String]): AbstractId = new AbstractId(pkg.init, pkg.last)
}
