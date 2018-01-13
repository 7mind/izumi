package org.bitbucket.pshirshov.izumi.di.model.plan

sealed trait PlanningOp

object PlanningOp {
  case class Put(op: ExecutableOp) extends PlanningOp

//  case class Replace(op: ExecutableOp, replacement: ExecutableOp) extends PlanningOp

  case class SolveUnsolvable(op: ExecutableOp, existing: DodgyOp) extends PlanningOp

  case class SolveRedefinition(op: ExecutableOp) extends PlanningOp
}



