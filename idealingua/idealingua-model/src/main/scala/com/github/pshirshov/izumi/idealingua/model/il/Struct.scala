package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, StructureId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ILAst.Super


case class Struct private
(
  id: StructureId
  , superclasses: Super
  , all: List[ExtendedField]
  , conflicts: FieldConflicts
) extends AbstractStruct[ExtendedField] {
  override protected def isLocal(f: ExtendedField): Boolean = {
    f.definedBy == id
  }
}

object Struct {
  def apply(id: StructureId, superclasses: Super, all: List[ExtendedField]): Struct = {
    val conflicts = FieldConflicts(all)

    // TODO: shitty side effect
    if (conflicts.hardConflicts.nonEmpty) {
      throw new IDLException(s"Conflicting fields: ${conflicts.hardConflicts}")
    }

    new Struct(id, superclasses, all, conflicts)
  }
}
