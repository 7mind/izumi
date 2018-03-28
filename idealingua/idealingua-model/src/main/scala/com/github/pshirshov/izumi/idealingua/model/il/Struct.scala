package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common.ExtendedField
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ILAst.Composite

case class Struct private(all: List[ExtendedField], conflicts: FieldConflicts, interfaces: Composite) {
  private def size: Int = all.size
  def isScalar: Boolean = size == 1
  def isComposite: Boolean = size > 1
  def isEmpty: Boolean = size == 0
  def nonEmpty: Boolean = !isEmpty
}

object Struct {
  def apply(all: List[ExtendedField], interfaces: Composite): Struct = {
    val sorted = all.sortBy(_.field.name)
    val conflicts = FieldConflicts(sorted)

    // TODO: shitty side effect
    if (conflicts.hardConflicts.nonEmpty) {
      throw new IDLException(s"Conflicting fields: ${conflicts.hardConflicts}")
    }

    new Struct(sorted, conflicts, interfaces)
  }
}
