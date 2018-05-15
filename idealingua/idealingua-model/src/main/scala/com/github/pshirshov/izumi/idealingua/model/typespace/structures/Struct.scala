package com.github.pshirshov.izumi.idealingua.model.typespace.structures

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.InterfaceId
import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, StructureId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Super


class Struct
(
  val id: StructureId
  , val superclasses: Super
  , val conflicts: FieldConflicts
) extends ConstAbstractStruct[ExtendedField] {
  override def all: List[ExtendedField] = {
    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
    conflicts.all.distinctBy(_.field).toList
  }

  override def unambigious: List[ExtendedField] = conflicts.goodFields.flatMap(_._2).toList

  override def ambigious: List[ExtendedField] = conflicts.softConflicts.flatMap(_._2).map(_._2.head).toList

  override protected def isLocal(f: ExtendedField): Boolean = {
    f.definedBy == id
  }

  def requiredInterfaces: List[InterfaceId] = {
    all
      .map(_.definedBy)
      .collect({ case i: InterfaceId => i })
      .distinct
  }
}

final case class PlainStruct(all: List[ExtendedField])

