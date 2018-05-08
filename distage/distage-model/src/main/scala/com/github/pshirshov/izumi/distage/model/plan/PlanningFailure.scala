package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse


sealed trait PlanningFailure {}

object PlanningFailure {
  final case class ConflictingOperation(target: RuntimeDIUniverse.DIKey, existing: ExecutableOp, conflicting: ExecutableOp) extends PlanningFailure
}
