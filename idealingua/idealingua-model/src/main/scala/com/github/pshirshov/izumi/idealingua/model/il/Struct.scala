package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, StructureId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ILAst.Super

trait AbstractStruct[F] {
  def all: List[F]

  def inherited: List[F] = all.filterNot(isLocal)

  def local: List[F] = all.filter(isLocal)

  def isScalar: Boolean = size == 1

  def isComposite: Boolean = size > 1

  def isEmpty: Boolean = size == 0

  def nonEmpty: Boolean = !isEmpty

  private def size: Int = all.size

  protected def isLocal(f: F): Boolean

}

case class Struct private
(
  id: StructureId
  , superclasses: Super
  , all: List[ExtendedField]
  , conflicts: FieldConflicts
) extends AbstractStruct[ExtendedField] {
  override def isLocal(f: ExtendedField): Boolean = {
    f.definedBy == id
  }
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
