package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{AbstractTypeId, TypeId}



sealed trait RawTypeDef {
  def id: TypeId
}

object RawTypeDef {

  case class Interface(id: InterfaceId, struct: RawStructure) extends RawTypeDef

  case class DTO(id: DTOId, struct: RawStructure) extends RawTypeDef

  case class Enumeration(id: EnumId, members: List[String]) extends RawTypeDef

  case class Alias(id: AliasId, target: AbstractTypeId) extends RawTypeDef

  case class Identifier(id: IdentifierId, fields: RawTuple) extends RawTypeDef

  case class Adt(id: AdtId, alternatives: List[RawAdtMember]) extends RawTypeDef

}





