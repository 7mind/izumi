package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._


sealed trait TypeDef {
  def id: TypeId
}


object TypeDef {

  final case class Alias(id: AliasId, target: TypeId) extends TypeDef

  final case class Enumeration(id: EnumId, members: List[String]) extends TypeDef

  final case class Adt(id: AdtId, alternatives: List[AdtMember]) extends TypeDef

  final case class Identifier(id: IdentifierId, fields: PrimitiveTuple) extends TypeDef

  sealed trait WithStructure extends TypeDef {
    def id: StructureId

    def struct: Structure
  }

  final case class Interface(id: InterfaceId, struct: Structure) extends WithStructure

  final case class DTO(id: DTOId, struct: Structure) extends WithStructure

}














