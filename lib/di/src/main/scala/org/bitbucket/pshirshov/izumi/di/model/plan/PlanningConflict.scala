package org.bitbucket.pshirshov.izumi.di.model.plan

trait PlanningConflict

object PlanningConflict {

  case class NoConflict(newOp: Op) extends PlanningConflict

  case class Conflict(newOp: Op, existingOp: Op) extends PlanningConflict

}