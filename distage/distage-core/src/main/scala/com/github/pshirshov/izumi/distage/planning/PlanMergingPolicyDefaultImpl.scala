package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.exceptions.UntranslatablePlanException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp._
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning.{PlanAnalyzer, PlanMergingPolicy}
import com.github.pshirshov.izumi.fundamentals.collections.Graphs

import scala.collection.mutable

sealed trait ConflictResolution

object ConflictResolution {

  final case class Successful(op: Set[ExecutableOp]) extends ConflictResolution

  final case class Failed(ops: Set[InstantiationOp]) extends ConflictResolution

}

class PlanMergingPolicyDefaultImpl(analyzer: PlanAnalyzer) extends PlanMergingPolicy {

  override def extendPlan(currentPlan: DodgyPlan, binding: Binding, currentOp: NextOps): DodgyPlan = {
    (currentOp.provisions ++ currentOp.sets.values).foreach {
      op =>
        val target = op.target
        currentPlan.operations.addBinding(target, op)
    }

    currentPlan
  }

  override def finalizePlan(completedPlan: DodgyPlan): SemiPlan = {
    val resolved = completedPlan.operations.mapValues(resolve).toMap
    val allOperations = resolved.values.collect({ case ConflictResolution.Successful(op) => op }).flatten.toSeq
    val issues = resolved.collect({ case (k, ConflictResolution.Failed(ops)) => (k, ops) }).toMap

    if (issues.nonEmpty) {
      // TODO: issues == slots, we may apply slot logic here
      throw new UntranslatablePlanException(s"Unresolved operation conflicts:\n${issues.mkString("\n")}", issues)
    }

    // it's not neccessary to sort the plan at this stage, it's gonna happen after GC
    SemiPlan(completedPlan.definition, allOperations.toVector)
  }

  def addImports(plan: SemiPlan): SemiPlan = {
    val topology = analyzer.topology(plan.steps)
    val imports = topology
      .dependees
      .graph
      .filterKeys(k => !plan.index.contains(k))
      .map {
        case (missing, refs) =>
          missing -> ImportDependency(missing, refs.toSet, None)
      }
      .toMap
    SemiPlan(plan.definition, (imports.values ++ plan.steps).toVector)
  }

  override def reorderOperations(completedPlan: SemiPlan): OrderedPlan = {
    val index = completedPlan.index
    val topology = analyzer.topology(completedPlan.steps)
    val sortedKeys = Graphs.toposort.cycleBreaking(
      topology.dependencies.graph
      , Seq.empty
    )

    val sortedOps = sortedKeys.flatMap(k => index.get(k).toSeq)
    OrderedPlan(completedPlan.definition, sortedOps.toVector, topology)
  }

  protected def resolve(operations: mutable.Set[InstantiationOp]): ConflictResolution = {
    operations match {
      case s if s.nonEmpty && s.forall(_.isInstanceOf[CreateSet]) =>
        val ops = s.collect({ case c: CreateSet => c })
        val merged = ops.tail.foldLeft(ops.head) {
          case (acc, op) =>
            acc.copy(members = acc.members ++ op.members)

        }
        ConflictResolution.Successful(Set(merged))
      case s if s.size == 1 =>
        ConflictResolution.Successful(Set(s.head))
      case other =>
        ConflictResolution.Failed(other.toSet)
    }
  }
}



