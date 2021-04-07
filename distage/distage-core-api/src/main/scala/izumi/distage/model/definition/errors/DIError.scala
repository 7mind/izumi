package izumi.distage.model.definition.errors

import izumi.distage.model.definition.conflicts.ConflictResolutionError
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.ExecutableOp.InstantiationOp
import izumi.distage.model.reflection.DIKey

sealed trait DIError

object DIError {

  sealed trait LoopResolutionError extends DIError

  object LoopResolutionError {
    final case class BUG_UnableToFindLoop(predcessors: Map[DIKey, Set[DIKey]]) extends LoopResolutionError
    final case class BUG_BestLoopResolutionIsNotSupported(op: ExecutableOp.SemiplanOp) extends LoopResolutionError
    final case class BestLoopResolutionCannotBeProxied(op: InstantiationOp) extends LoopResolutionError
  }

  final case class ConflictResolutionFailed(error: ConflictResolutionError[DIKey, InstantiationOp]) extends DIError


}