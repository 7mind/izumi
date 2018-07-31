package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.{AbstractIndefiniteId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._


sealed trait RawTypeDef {
  def doc: Option[String]
}

sealed trait IdentifiedRawTypeDef extends RawTypeDef {
  def id: TypeId
}

object RawTypeDef {

  final case class Interface(id: InterfaceId, struct: RawStructure, doc: Option[String]) extends IdentifiedRawTypeDef

  final case class DTO(id: DTOId, struct: RawStructure, doc: Option[String]) extends IdentifiedRawTypeDef

  final case class Enumeration(id: EnumId, members: List[String], doc: Option[String]) extends IdentifiedRawTypeDef

  final case class Alias(id: AliasId, target: AbstractIndefiniteId, doc: Option[String]) extends IdentifiedRawTypeDef

  final case class Identifier(id: IdentifierId, fields: RawTuple, doc: Option[String]) extends IdentifiedRawTypeDef

  final case class Adt(id: AdtId, alternatives: List[RawAdtMember], doc: Option[String]) extends IdentifiedRawTypeDef

  final case class NewType(id: ParsedId, source: AbstractIndefiniteId, modifiers: Option[RawStructure], doc: Option[String]) extends RawTypeDef

}





