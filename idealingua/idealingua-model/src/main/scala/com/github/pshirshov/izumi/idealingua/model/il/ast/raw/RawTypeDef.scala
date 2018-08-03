package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{AbstractIndefiniteId, TypeId}


sealed trait RawTypeDef {
  def meta: RawNodeMeta
}

sealed trait IdentifiedRawTypeDef extends RawTypeDef {
  def id: TypeId
}

case class RawNodeMeta(doc: Option[String], annos: Seq[RawAnno])

object RawTypeDef {

  final case class Interface(id: InterfaceId, struct: RawStructure, meta: RawNodeMeta) extends IdentifiedRawTypeDef

  final case class DTO(id: DTOId, struct: RawStructure, meta: RawNodeMeta) extends IdentifiedRawTypeDef

  final case class Enumeration(id: EnumId, members: List[String], meta: RawNodeMeta) extends IdentifiedRawTypeDef

  final case class Alias(id: AliasId, target: AbstractIndefiniteId, meta: RawNodeMeta) extends IdentifiedRawTypeDef

  final case class Identifier(id: IdentifierId, fields: RawTuple, meta: RawNodeMeta) extends IdentifiedRawTypeDef

  final case class Adt(id: AdtId, alternatives: List[RawAdtMember], meta: RawNodeMeta) extends IdentifiedRawTypeDef

  final case class NewType(id: ParsedId, source: AbstractIndefiniteId, modifiers: Option[RawStructure], meta: RawNodeMeta) extends RawTypeDef

}


case class RawAnno(name: String, values: RawVal.CMap)
