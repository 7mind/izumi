package com.github.pshirshov.izumi.distage.model.plan

trait PlanningConflict

object PlanningConflict {

  final case class NoConflict(newOp: ExecutableOp) extends PlanningConflict

  final case class Conflict(newOp: ExecutableOp, existingOp: PlanningFailure) extends PlanningConflict

}
