package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{AbstractIndefiniteId, IndefiniteGeneric, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition


sealed trait RawTypeDef

sealed trait IdentifiedRawTypeDef extends RawTypeDef {
  def id: TypeId
}

case class RawEnumMember(value: String, meta: RawNodeMeta) {
  override def toString: String = value
}

case class RawNodeMeta(doc: Option[String], annos: Seq[RawAnno], position: InputPosition)

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


case class RawAnno(name: String, values: RawVal.CMap, position: InputPosition)
