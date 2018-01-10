package org.bitbucket.pshirshov.izumi.di.model.plan

trait PlanningConflict

object PlanningConflict {

  case class NoConflict(newOp: ExecutableOp) extends PlanningConflict

  case class Conflict(newOp: ExecutableOp, existingOp: DodgyOp) extends PlanningConflict

}