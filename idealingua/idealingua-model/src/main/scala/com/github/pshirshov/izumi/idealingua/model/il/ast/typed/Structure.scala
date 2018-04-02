package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.{Primitive, TypeId}
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.InterfaceId

case class Field(typeId: TypeId, name: String)

case class PrimitiveField(typeId: Primitive, name: String)

case class SimpleStructure(concepts: Composite, fields: Tuple)


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
