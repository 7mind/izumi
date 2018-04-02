package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{AbstractTypeId, TypeId}

case class Field(typeId: AbstractTypeId, name: String)


case class Structure(interfaces: Composite, concepts: Composite, removedConcepts: Composite, fields: Aggregate, removedFields: Aggregate)

case class SimpleStructure(concepts: Composite, fields: Aggregate)

sealed trait ILAstParsed {
  def id: TypeId
}

object ILAstParsed {

  case class Interface(id: InterfaceId, struct: Structure) extends ILAstParsed

  case class DTO(id: DTOId, struct: Structure) extends ILAstParsed

  case class Enumeration(id: EnumId, members: List[String]) extends ILAstParsed

  case class Alias(id: AliasId, target: AbstractTypeId) extends ILAstParsed

  case class Identifier(id: IdentifierId, fields: Aggregate) extends ILAstParsed

  case class Adt(id: AdtId, alternatives: Types) extends ILAstParsed

}




