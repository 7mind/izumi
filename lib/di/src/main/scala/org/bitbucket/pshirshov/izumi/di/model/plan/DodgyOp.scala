package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.definition.Def


sealed trait DodgyOp {
  def op: ExecutableOp
}

object DodgyOp {

  case class Statement(op: ExecutableOp) extends DodgyOp

  case class DuplicatedStatement(op: ExecutableOp) extends DodgyOp

  case class UnsolvableConflict(op: ExecutableOp, existing: ExecutableOp) extends DodgyOp

  case class UnbindableBinding(implDef: Def) extends DodgyOp {
    override def op: ExecutableOp = ???
  }

}