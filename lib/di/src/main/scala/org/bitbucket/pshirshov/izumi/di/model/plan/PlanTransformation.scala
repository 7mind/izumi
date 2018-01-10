package org.bitbucket.pshirshov.izumi.di.model.plan

sealed trait PlanTransformation

object PlanTransformation {

  case class Put(op: Op) extends PlanTransformation

  case class Replace(op: Op, replacement: Op) extends PlanTransformation

  case class SolveUnsolvable(op: Op, existing: Op) extends PlanTransformation

  case class SolveRedefinition(op: Op) extends PlanTransformation
}



