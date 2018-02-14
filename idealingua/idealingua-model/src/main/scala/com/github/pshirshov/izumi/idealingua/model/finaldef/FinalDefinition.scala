package com.github.pshirshov.izumi.idealingua.model.finaldef

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.finaldef.FinalDefinition.Composite

sealed trait FinalDefinition {
  def id: TypeId
}


object FinalDefinition {
  type Composite = Seq[InterfaceId]
  type Aggregate = Seq[Field]

  case class Enumeration(id: EnumId, members: List[String]) extends FinalDefinition

  case class Alias(id: AliasId, target: TypeId) extends FinalDefinition

  case class Identifier(id: IdentifierId, fields: Aggregate) extends FinalDefinition

  case class Interface(id: InterfaceId, fields: Aggregate, interfaces: Composite) extends FinalDefinition

  case class DTO(id: DTOId, interfaces: Composite) extends FinalDefinition

}

case class Signature(input: Composite, output: Composite)


case class Service(id: ServiceId, methods: Seq[DefMethod])






