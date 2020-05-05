package izumi.distage.model.planning

import izumi.distage.model.definition.Activation
import izumi.distage.model.plan.initial.PrePlan.{JustOp, SetOp, TraceableOp}
import izumi.distage.model.plan.ExecutableOp.SemiplanOp
import izumi.distage.model.plan.SemiPlan
import izumi.distage.model.plan.initial.PrePlan
import izumi.distage.model.reflection._

trait PlanMergingPolicy {
  def freeze(activation: Activation, plan: PrePlan): SemiPlan
}

object PlanMergingPolicy {

  sealed trait DIKeyConflictResolution
  object DIKeyConflictResolution {
    final case class Successful(op: Set[SemiplanOp]) extends DIKeyConflictResolution
    final case class Failed(candidates: Set[SemiplanOp], explanation: String) extends DIKeyConflictResolution
  }

  trait WithResolve {
    this: PlanMergingPolicy =>
    final protected def resolve(activation: Activation, plan: PrePlan, key: DIKey, operations: Set[TraceableOp]): DIKeyConflictResolution = {
      operations match {
        case s if s.size == 1 =>
          DIKeyConflictResolution.Successful(Set(s.head.op))
        case s if s.nonEmpty && s.forall(_.isInstanceOf[SetOp]) =>
          val ops = s.collect { case c: SetOp => c.op }
          val merged = ops.tail.foldLeft(ops.head) {
            case (acc, op) =>
              acc.copy(members = acc.members ++ op.members)
          }
          DIKeyConflictResolution.Successful(Set(merged))
        case s if s.nonEmpty && s.forall(_.isInstanceOf[JustOp]) =>
          resolveConflict(activation, plan, key, s.collect { case c: JustOp => c })
        case s if s.exists(_.isInstanceOf[JustOp]) && s.exists(_.isInstanceOf[SetOp]) =>
          DIKeyConflictResolution.Failed(operations.map(_.op), "Set and non-set bindings to the same key")
        case other =>
          DIKeyConflictResolution.Failed(other.map(_.op), "Unsupported combinations of operations")
      }
    }

    protected def resolveConflict(activation: Activation, plan: PrePlan, key: DIKey, operations: Set[JustOp]): DIKeyConflictResolution
  }
}
