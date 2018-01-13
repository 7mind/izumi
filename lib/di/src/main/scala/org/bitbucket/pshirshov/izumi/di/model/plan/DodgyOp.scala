package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.definition.{Binding, ImplDef}


sealed trait DodgyOp {}

object DodgyOp {
  case class Statement(op: ExecutableOp) extends DodgyOp

  case class Nop(message: String) extends DodgyOp

  case class DuplicatedStatement(op: ExecutableOp) extends DodgyOp

  case class UnsolvableConflict(op: ExecutableOp, existing: DodgyOp) extends DodgyOp

  case class UnbindableBinding(binding: Binding, defs: Seq[ImplDef]) extends DodgyOp
}