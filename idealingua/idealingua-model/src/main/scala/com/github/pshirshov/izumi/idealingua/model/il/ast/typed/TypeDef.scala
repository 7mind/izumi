package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._


sealed trait TypeDef {
  def id: TypeId
}

object TypeDef {

  case class Alias(id: AliasId, target: TypeId) extends TypeDef

  case class Enumeration(id: EnumId, members: List[String]) extends TypeDef

  case class Adt(id: AdtId, alternatives: Types) extends TypeDef

  case class Identifier(id: IdentifierId, fields: PrimitiveTuple) extends TypeDef

  sealed trait WithStructure extends TypeDef {
    def id: StructureId

    def struct: Structure
  }

  case class Interface(id: InterfaceId, struct: Structure) extends WithStructure

  case class DTO(id: DTOId, struct: Structure) extends WithStructure

}














