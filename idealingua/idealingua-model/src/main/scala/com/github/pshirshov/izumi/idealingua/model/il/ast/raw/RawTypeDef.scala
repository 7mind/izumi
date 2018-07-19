package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.{AbstractIndefiniteId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._



sealed trait RawTypeDef {
}

sealed trait IdentifiedRawTypeDef extends RawTypeDef {
  def id: TypeId
}
object RawTypeDef {

  final case class Interface(id: InterfaceId, struct: RawStructure) extends IdentifiedRawTypeDef

  final case class DTO(id: DTOId, struct: RawStructure) extends IdentifiedRawTypeDef

  final case class Enumeration(id: EnumId, members: List[String]) extends IdentifiedRawTypeDef

  final case class Alias(id: AliasId, target: AbstractIndefiniteId) extends IdentifiedRawTypeDef

  final case class Identifier(id: IdentifierId, fields: RawTuple) extends IdentifiedRawTypeDef

  final case class Adt(id: AdtId, alternatives: List[RawAdtMember]) extends IdentifiedRawTypeDef

  final case class NewType(id: ParsedId, source: AbstractIndefiniteId, modifiers: Option[RawStructure]) extends RawTypeDef

}





