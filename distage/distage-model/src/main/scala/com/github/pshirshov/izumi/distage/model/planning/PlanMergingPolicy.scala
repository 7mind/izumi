package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.DodgyPlan.{JustOp, SetOp, TraceableOp}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.InstantiationOp
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, ExecutableOp, SemiPlan}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

trait PlanMergingPolicy {

  def freeze(completedPlan: DodgyPlan): SemiPlan

}

object PlanMergingPolicy {

  sealed trait DIKeyConflictResolution

  object DIKeyConflictResolution {

    final case class Successful(op: Set[ExecutableOp]) extends DIKeyConflictResolution

    final case class Failed(ops: Set[InstantiationOp]) extends DIKeyConflictResolution
  }

  trait WithResolve {
    this: PlanMergingPolicy =>
    final protected def resolve(key: DIKey, operations: Set[TraceableOp]): DIKeyConflictResolution = {
      operations match {
        case s if s.size == 1 =>
          DIKeyConflictResolution.Successful(Set(s.head.op))
        case s if s.nonEmpty && s.forall(_.isInstanceOf[SetOp]) =>
          val ops = s.collect({ case c: SetOp => c.op })
          val merged = ops.tail.foldLeft(ops.head) {
            case (acc, op) =>
              acc.copy(members = acc.members ++ op.members)
          }
          DIKeyConflictResolution.Successful(Set(merged))
        case s if s.nonEmpty && s.forall(_.isInstanceOf[JustOp]) =>
          resolveConflict(key, s.collect({ case c: JustOp => c }))
        case s if s.exists(_.isInstanceOf[JustOp]) && s.exists(_.isInstanceOf[SetOp]) =>
          DIKeyConflictResolution.Failed(operations.map(_.op))
        case other =>
          DIKeyConflictResolution.Failed(operations.map(_.op))
      }
    }

    protected def resolveConflict(key: DIKey, operations: Set[JustOp]): DIKeyConflictResolution = {
      DIKeyConflictResolution.Failed(operations.map(_.op))
    }
  }
}
