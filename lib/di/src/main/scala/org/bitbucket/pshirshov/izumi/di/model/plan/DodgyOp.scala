package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.definition.Binding


sealed trait DodgyOp {
  //def op: ExecutableOp
}

object DodgyOp {
  case class Statement(op: ExecutableOp) extends DodgyOp

  case class DuplicatedStatement(op: ExecutableOp) extends DodgyOp

  case class UnsolvableConflict(op: ExecutableOp, existing: DodgyOp) extends DodgyOp

  case class Nop(message: String) extends DodgyOp

  case class UnbindableBinding(implDef: Binding) extends DodgyOp
}