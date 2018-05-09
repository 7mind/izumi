package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{AbstractIndefiniteId, TypeId}



sealed trait RawTypeDef {
  def id: TypeId
}

object RawTypeDef {

  final case class Interface(id: InterfaceId, struct: RawStructure) extends RawTypeDef

  final case class DTO(id: DTOId, struct: RawStructure) extends RawTypeDef

  final case class Enumeration(id: EnumId, members: List[String]) extends RawTypeDef

  final case class Alias(id: AliasId, target: AbstractIndefiniteId) extends RawTypeDef

  final case class Identifier(id: IdentifierId, fields: RawTuple) extends RawTypeDef

  final case class Adt(id: AdtId, alternatives: List[RawAdtMember]) extends RawTypeDef

}





