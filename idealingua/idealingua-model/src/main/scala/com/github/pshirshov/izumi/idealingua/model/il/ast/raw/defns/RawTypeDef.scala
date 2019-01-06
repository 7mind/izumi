package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{AbstractIndefiniteId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.ParsedId


sealed trait RawTypeDef


case class InterpContext(parts: Seq[String], parameters: Seq[AbstractIndefiniteId])

object RawTypeDef {

  sealed trait WithId extends RawTypeDef {
    def id: TypeId
  }


  final case class Interface(id: InterfaceId, struct: RawStructure, meta: RawNodeMeta) extends WithId

  final case class DTO(id: DTOId, struct: RawStructure, meta: RawNodeMeta) extends WithId

  final case class Enumeration(id: EnumId, struct: RawEnum, meta: RawNodeMeta) extends WithId

  final case class Alias(id: AliasId, target: AbstractIndefiniteId, meta: RawNodeMeta) extends WithId

  final case class Identifier(id: IdentifierId, fields: RawTuple, meta: RawNodeMeta) extends WithId

  final case class Adt(id: AdtId, alternatives: List[Member], meta: RawNodeMeta) extends WithId

  final case class NewType(id: ParsedId, source: AbstractIndefiniteId, modifiers: Option[RawStructure], meta: RawNodeMeta) extends RawTypeDef

  final case class ForeignType(id: AbstractIndefiniteId, mapping: Map[String, InterpContext], meta: RawNodeMeta) extends RawTypeDef

}



