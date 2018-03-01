package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.FinalDefinition.Composite
import com.github.pshirshov.izumi.idealingua.model.il.Service.DefMethod

sealed trait FinalDefinition {
  def id: TypeId
}


object FinalDefinition {
  type Composite = List[InterfaceId]
  type Aggregate = List[Field]

  case class Enumeration(id: EnumId, members: List[String]) extends FinalDefinition

  case class Alias(id: AliasId, target: TypeId) extends FinalDefinition

  case class Identifier(id: IdentifierId, fields: Aggregate) extends FinalDefinition

  case class Interface(id: InterfaceId, fields: Aggregate, interfaces: Composite, concepts: Composite) extends FinalDefinition

  case class DTO(id: DTOId, interfaces: Composite, concepts: Composite) extends FinalDefinition

  case class Adt(id: AdtId, alternatives: List[TypeId]) extends FinalDefinition
}



case class Service(id: ServiceId, methods: List[DefMethod])

object Service {
  trait DefMethod

  object DefMethod {
    case class Signature(input: Composite, output: Composite) {
      def asList: List[InterfaceId] = input ++ output
    }

    case class RPCMethod(name: String, signature: Signature) extends DefMethod
  }
}






