package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{DomainId, _}

final case class ParsedId(pkg: Seq[String], name: String) {
  private def typePath = {
    if (pkg.isEmpty) {
      TypePath(DomainId.Undefined, Seq.empty)
    } else {
      TypePath(DomainId(pkg.init, pkg.last), Seq.empty)

    }
  }

  def toEnumId: EnumId = EnumId(typePath, name)

  def toAliasId: AliasId = AliasId(typePath, name)

  def toIdId: IdentifierId = IdentifierId(typePath, name)

  def toInterfaceId: InterfaceId = InterfaceId(typePath, name)

  def toParentId: InterfaceId = toInterfaceId

  def toMixinId: IndefiniteMixin = IndefiniteMixin(pkg, name)

  def toDataId: DTOId = DTOId(typePath, name)

  def toAdtId: AdtId = AdtId(typePath, name)

  def toServiceId: ServiceId = ServiceId(typePath.domain, name)

  def toBuzzerId: BuzzerId = BuzzerId(typePath.domain, name)

  def toStreamsId: StreamsId = StreamsId(typePath.domain, name)

  def toConstId: ConstId = ConstId(typePath.domain, name)

  def toTypeId: AbstractIndefiniteId = {
    IndefiniteId(pkg, name)
  }

  def toGeneric(params: Seq[Seq[AbstractIndefiniteId]]): AbstractIndefiniteId = {
    if (params.nonEmpty) {
      IndefiniteGeneric(pkg, name, params.flatten.toList)
    } else {
      toTypeId
    }
  }
}

object ParsedId {
  def apply(name: String): ParsedId = new ParsedId(Seq.empty, name)
}
