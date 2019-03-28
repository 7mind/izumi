package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.{RawDeclaredTypeName, RawRef}

final case class RawClone(
                           interfaces: RawInterfaces,
                           concepts: RawStructures,
                           removedConcepts: RawStructures,
                           removedParents: RawStructures,
                           fields: RawTuple,
                           removedFields: RawTuple,
                           branches: Seq[RawAdt.Member],
                           removedBranches: Seq[RawDeclaredTypeName],
                         ) {
  def extend(other: RawClone): RawClone = {
    this.copy(
      interfaces = interfaces ++ other.interfaces
      , concepts = concepts ++ other.concepts
      , removedConcepts = removedConcepts ++ other.removedConcepts
      , fields = fields ++ other.fields
      , removedFields = removedFields ++ other.removedFields
    )
  }
}

object RawClone {

  sealed trait CloneOp

  object CloneOp {

    final case class Extend(tpe: RawRef) extends CloneOp

    final case class Mix(tpe: RawRef) extends CloneOp

    final case class AddBranch(tpe: RawAdt.Member) extends CloneOp

    final case class RemoveBranch(name: RawDeclaredTypeName) extends CloneOp

    final case class DropConcept(tpe: RawRef) extends CloneOp

    final case class DropParent(tpe: RawRef) extends CloneOp

    final case class AddField(field: RawField) extends CloneOp

    final case class RemoveField(field: RawField) extends CloneOp

  }

  def apply(v: Seq[CloneOp]): RawClone = {
    import CloneOp._
    RawClone(
      v.collect({ case Extend(i) => i }).toList
      , v.collect({ case Mix(i) => i }).toList
      , v.collect({ case DropConcept(i) => i }).toList
      , v.collect({ case DropParent(i) => i }).toList
      , v.collect({ case AddField(i) => i }).toList
      , v.collect({ case RemoveField(i) => i }).toList
      , v.collect({ case AddBranch(i) => i }).toList
      , v.collect({ case RemoveBranch(i) => i }).toList
    )
  }
}
