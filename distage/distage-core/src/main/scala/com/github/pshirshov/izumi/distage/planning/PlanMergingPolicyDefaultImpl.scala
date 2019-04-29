package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.exceptions.ConflictingDIKeyBindingsException
import com.github.pshirshov.izumi.distage.model.plan.DodgyPlan.{JustOp, SetOp, TraceableOp}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp._
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning.PlanMergingPolicy
import com.github.pshirshov.izumi.distage.planning.PlanMergingPolicyDefaultImpl.DIKeyConflictResolution
import distage.DIKey

class PlanMergingPolicyDefaultImpl() extends PlanMergingPolicy {

  override final def freeze(completedPlan: DodgyPlan): SemiPlan = {
    val resolved = completedPlan.freeze.map({case (k, v) => k -> resolve(k, v)}).toMap

    val allOperations = resolved.values.collect { case DIKeyConflictResolution.Successful(op) => op }.flatten.toSeq
    val issues = resolved.collect { case (k, DIKeyConflictResolution.Failed(ops)) => (k, ops) }.toMap

    if (issues.nonEmpty) {
      import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
      // TODO: issues == slots, we may apply slot logic here
      val issueRepr = issues.map {
        case (k, ops) =>
          s"Conflicting bindings found for key $k: ${ops.niceList().shift(2)}"
      }
      throw new ConflictingDIKeyBindingsException(
        s"""There must be exactly one valid binding for each DIKey.
           |
           |You can use named instances: `make[X].named("id")` method and `distage.Id` annotation to disambiguate
           |between multiple instances of the same type.
           |
           |List of problematic bindings: ${issueRepr.niceList()}
         """.stripMargin
        , issues
      )
    }

    // it's not neccessary to sort the plan at this stage, it's gonna happen after GC
    SemiPlan(completedPlan.definition, allOperations.toVector, completedPlan.roots)
  }

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

object PlanMergingPolicyDefaultImpl {

  sealed trait DIKeyConflictResolution

  object DIKeyConflictResolution {

    final case class Successful(op: Set[ExecutableOp]) extends DIKeyConflictResolution

    final case class Failed(ops: Set[InstantiationOp]) extends DIKeyConflictResolution
  }
}
