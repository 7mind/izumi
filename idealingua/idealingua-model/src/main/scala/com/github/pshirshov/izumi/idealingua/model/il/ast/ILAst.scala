package com.github.pshirshov.izumi.idealingua.model.il.ast

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._

sealed trait ILAst {
  def id: TypeId
}


sealed trait ILStructure extends ILAst {
  def id: StructureId
  def struct: ILAst.Structure
}

object ILAst {

  case class Field(typeId: TypeId, name: String)
  case class PrimitiveField(typeId: Primitive, name: String)

  type Composite = List[InterfaceId]
  type Tuple = List[Field]
  type PrimitiveTuple = List[PrimitiveField]

  case class Super(
                    interfaces: Composite
                    , concepts: Composite
                    , removedConcepts: Composite
                  ) {
    val all: List[InterfaceId] = interfaces ++ concepts
  }

  object Super {
    def empty: Super = Super(List.empty, List.empty, List.empty)
  }


  case class Structure(fields: Tuple, removedFields: Tuple, superclasses: Super)

  object Structure {
    def empty: Structure = Structure(List.empty, List.empty, Super.empty)

    def interfaces(ids: List[InterfaceId]): Structure= Structure(List.empty, List.empty, Super(ids, List.empty, List.empty))

  }

  case class Identifier(id: IdentifierId, fields: PrimitiveTuple) extends ILAst

  case class Interface(id: InterfaceId, struct: Structure) extends ILStructure

  case class DTO(id: DTOId, struct: Structure) extends ILStructure

  case class Enumeration(id: EnumId, members: List[String]) extends ILAst

  case class Alias(id: AliasId, target: TypeId) extends ILAst

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










