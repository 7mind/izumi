package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{AbstractIndefiniteId, TypeId}


sealed trait RawTypeDef

sealed trait IdentifiedRawTypeDef extends RawTypeDef {
  def id: TypeId
}


object RawTypeDef {

  final case class Interface(id: InterfaceId, struct: RawStructure, meta: RawNodeMeta) extends IdentifiedRawTypeDef

  final case class DTO(id: DTOId, struct: RawStructure, meta: RawNodeMeta) extends IdentifiedRawTypeDef

  final case class Enumeration(id: EnumId, members: List[RawEnumMember], meta: RawNodeMeta) extends IdentifiedRawTypeDef

  final case class Alias(id: AliasId, target: AbstractIndefiniteId, meta: RawNodeMeta) extends IdentifiedRawTypeDef

  final case class Identifier(id: IdentifierId, fields: RawTuple, meta: RawNodeMeta) extends IdentifiedRawTypeDef

  final case class Adt(id: AdtId, alternatives: List[RawAdtMember], meta: RawNodeMeta) extends IdentifiedRawTypeDef

  final case class NewType(id: ParsedId, source: AbstractIndefiniteId, modifiers: Option[RawStructure], meta: RawNodeMeta) extends RawTypeDef

  final case class ForeignType(id: AbstractIndefiniteId, mapping: Map[String, String], meta: RawNodeMeta) extends RawTypeDef

}



