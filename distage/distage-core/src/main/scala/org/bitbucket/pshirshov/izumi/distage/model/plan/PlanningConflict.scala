package org.bitbucket.pshirshov.izumi.distage.model.plan

trait PlanningConflict

object PlanningConflict {

  case class NoConflict(newOp: ExecutableOp) extends PlanningConflict

  case class Conflict(newOp: ExecutableOp, existingOp: PlanningFailure) extends PlanningConflict

}