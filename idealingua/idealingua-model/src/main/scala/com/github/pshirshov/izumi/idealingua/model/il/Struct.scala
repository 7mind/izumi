package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, StructureId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ILAst.Super

case class Struct private(
                           id: StructureId
                           , superclasses: Super
                           , all: List[ExtendedField]
                           , conflicts: FieldConflicts
                         ) {
  private def size: Int = all.size
  def isScalar: Boolean = size == 1
  def isComposite: Boolean = size > 1
  def isEmpty: Boolean = size == 0
  def nonEmpty: Boolean = !isEmpty
}

object Struct {
  def apply(id: StructureId, superclasses: Super, all: List[ExtendedField]): Struct = {
    val sorted = all.sortBy(_.field.name)
    val conflicts = FieldConflicts(sorted)

    // TODO: shitty side effect
    if (conflicts.hardConflicts.nonEmpty) {
      throw new IDLException(s"Conflicting fields: ${conflicts.hardConflicts}")
    }

    new Struct(id, superclasses, sorted, conflicts)
  }
}
