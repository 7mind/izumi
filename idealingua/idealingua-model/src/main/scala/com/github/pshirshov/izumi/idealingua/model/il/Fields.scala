package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common.ExtendedField
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException

case class Fields private (all: List[ExtendedField], conflicts: FieldConflicts) {
  def size: Int = all.size
}

object Fields {
  def apply(all: List[ExtendedField]): Fields = {
    val sorted = all.sortBy(_.field.name)
    val conflicts = FieldConflicts(sorted)

    // TODO: shitty side effect
    if (conflicts.hardConflicts.nonEmpty) {
      throw new IDLException(s"Conflicting fields: ${conflicts.hardConflicts}")
    }

    new Fields(sorted, conflicts)
  }
}
