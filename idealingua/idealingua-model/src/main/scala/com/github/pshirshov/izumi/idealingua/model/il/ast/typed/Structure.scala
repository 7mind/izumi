package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.{Primitive, TypeId}
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.InterfaceId

final case class Field(typeId: TypeId, name: String) {
  override def toString: String = s"$name:$typeId"
}

final case class PrimitiveField(typeId: Primitive, name: String)

final case class SimpleStructure(concepts: Interfaces, fields: Tuple)


final case class Super(
                  interfaces: Interfaces
                  , concepts: Interfaces
                  , removedConcepts: Interfaces
                ) {
  val all: List[InterfaceId] = interfaces ++ concepts
}

object Super {
  def empty: Super = Super(List.empty, List.empty, List.empty)
}

final case class Structure(fields: Tuple, removedFields: Tuple, superclasses: Super)

object Structure {
  def empty: Structure = Structure(List.empty, List.empty, Super.empty)

  def interfaces(ids: List[InterfaceId]): Structure= Structure(List.empty, List.empty, Super(ids, List.empty, List.empty))

}
