package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._

sealed trait ILAst {
  def id: TypeId
}


object ILAst {
  case class Field(typeId: TypeId, name: String)

  type Composite = List[InterfaceId]
  type Aggregate = List[Field]

  case class Enumeration(id: EnumId, members: List[String]) extends ILAst

  case class Alias(id: AliasId, target: TypeId) extends ILAst

  case class Identifier(id: IdentifierId, fields: Aggregate) extends ILAst

  case class Interface(id: InterfaceId, fields: Aggregate, interfaces: Composite, concepts: Composite) extends ILAst

  case class DTO(id: DTOId, interfaces: Composite, concepts: Composite) extends ILAst

  case class Adt(id: AdtId, alternatives: List[TypeId]) extends ILAst

  case class Service(id: ServiceId, methods: List[Service.DefMethod])

  object Service {

    trait DefMethod

    object DefMethod {

      case class Signature(input: Composite, output: Composite) {
        def asList: List[InterfaceId] = input ++ output
      }

      case class RPCMethod(name: String, signature: Signature) extends DefMethod

    }

  }

}

sealed trait ILAstParsed {
  def id: TypeId
}


object ILAstParsed {
  case class Field(typeId: AbstractTypeId, name: String)

  type Composite = List[InterfaceId]
  type Aggregate = List[Field]

  case class Enumeration(id: EnumId, members: List[String]) extends ILAstParsed

  case class Alias(id: AliasId, target: AbstractTypeId) extends ILAstParsed

  case class Identifier(id: IdentifierId, fields: Aggregate) extends ILAstParsed

  case class Interface(id: InterfaceId, fields: Aggregate, interfaces: Composite, concepts: Composite) extends ILAstParsed

  case class DTO(id: DTOId, interfaces: Composite, concepts: Composite) extends ILAstParsed

  case class Adt(id: AdtId, alternatives: List[AbstractTypeId]) extends ILAstParsed

  case class Service(id: ServiceId, methods: List[Service.DefMethod])

  object Service {

    trait DefMethod

    object DefMethod {

      case class Signature(input: Composite, output: Composite) {
        def asList: List[InterfaceId] = input ++ output
      }

      case class RPCMethod(name: String, signature: Signature) extends DefMethod

    }

  }

}





