package org.bitbucket.pshirshov.izumi.di.model.plan

sealed trait PlanMetaStep {
  def op: Op
}

object PlanMetaStep {

  case class Statement(op: Op) extends PlanMetaStep

  case class Duplicate(op: Op) extends PlanMetaStep

  case class ConflictingStatement(op: Op, existing: Op) extends PlanMetaStep
}