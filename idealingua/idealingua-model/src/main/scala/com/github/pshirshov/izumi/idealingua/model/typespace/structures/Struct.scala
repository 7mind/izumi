package com.github.pshirshov.izumi.idealingua.model.typespace.structures

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.InterfaceId
import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, StructureId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Super


class Struct private
(
  val id: StructureId
  , val superclasses: Super
  , conflicts: FieldConflicts
) extends ConstAbstractStruct[ExtendedField] {
  override def all: List[ExtendedField] = conflicts.all.toList

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

object Struct {
  def apply(id: StructureId, superclasses: Super, all: List[ExtendedField]): Struct = {
    val conflicts = FieldConflicts(all)

    // TODO: shitty side effect
    if (conflicts.hardConflicts.nonEmpty) {
      throw new IDLException(s"Conflicting fields: ${conflicts.hardConflicts}")
    }

    new Struct(id, superclasses, conflicts)
  }
}

case class PlainStruct(all: List[ExtendedField])
