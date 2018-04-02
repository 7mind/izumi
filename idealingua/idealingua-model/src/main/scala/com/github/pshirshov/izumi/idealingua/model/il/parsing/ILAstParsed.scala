package com.github.pshirshov.izumi.idealingua.model.il.parsing

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{AbstractTypeId, TypeId}

sealed trait ILAstParsed {
  def id: TypeId
}

object ILAstParsed {
  case class Field(typeId: AbstractTypeId, name: String)

  type Composite = List[InterfaceId]
  type Aggregate = List[Field]
  type Types = List[AbstractTypeId]

  case class Structure(interfaces: Composite, concepts: Composite, removedConcepts: Composite, fields: Aggregate, removedFields: Aggregate)
  case class SimpleStructure(concepts: Composite, fields: Aggregate)

  case class Interface(id: InterfaceId, struct: Structure) extends ILAstParsed

  case class DTO(id: DTOId, struct: Structure) extends ILAstParsed

  case class Enumeration(id: EnumId, members: List[String]) extends ILAstParsed

  case class Alias(id: AliasId, target: AbstractTypeId) extends ILAstParsed

  case class Identifier(id: IdentifierId, fields: Aggregate) extends ILAstParsed

  case class Adt(id: AdtId, alternatives: Types) extends ILAstParsed

  case class Service(id: ServiceId, methods: List[Service.DefMethod])

  object Service {

    trait DefMethod

    object DefMethod {

      sealed trait Output

      object Output {
        case class Usual(input: SimpleStructure) extends Output
        case class Algebraic(alternatives: Types) extends Output
      }

      case class Signature(input: SimpleStructure, output: Output)

      case class RPCMethod(name: String, signature: Signature) extends DefMethod

      @deprecated
      case class DeprecatedSignature(input: Composite, output: Composite) {
        def asList: List[InterfaceId] = input ++ output
      }

      @deprecated
      case class DeprecatedMethod(name: String, signature: DeprecatedSignature) extends DefMethod
    }

  }

}
