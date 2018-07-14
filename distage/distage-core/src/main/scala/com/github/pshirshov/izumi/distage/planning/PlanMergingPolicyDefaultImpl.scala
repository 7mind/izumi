package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.exceptions.SanityCheckFailedException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp._
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning.{PlanAnalyzer, PlanMergingPolicy}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.collections.Graphs

import scala.collection.mutable

class PlanMergingPolicyDefaultImpl(analyzer: PlanAnalyzer) extends PlanMergingPolicy {

  override def extendPlan(currentPlan: DodgyPlan, binding: Binding, currentOp: NextOps): DodgyPlan = {
    (currentOp.provisions ++ currentOp.sets.values).foreach {
      op =>
        val target = op.target

        val issues = findIssues(currentPlan, op)
        if (issues.isEmpty) {
          val old = currentPlan.operations.get(target)
          val merged = merge(old, op)
          currentPlan.operations.put(target, merged)
          analyzer.topoExtend(currentPlan.topology, op)
        } else {
          currentPlan.issues ++= issues
        }
    }

    currentPlan
  }

  private def merge(old: Option[InstantiationOp], op: InstantiationOp): InstantiationOp = {
    (old, op) match {
      case (Some(oldset: CreateSet), newset: CreateSet) =>
        newset.copy(members = oldset.members ++ newset.members)
      case (None, newop) =>
        newop
      case other =>
        throw new SanityCheckFailedException(s"Unexpected pair: $other")
    }
  }

  private def findIssues(currentPlan: DodgyPlan, op: InstantiationOp) = {
    val target = op.target

    val issues = mutable.ArrayBuffer.empty[PlanningFailure]

    currentPlan.operations.get(target) match {
      case Some(existing) =>
        (existing, op) match {
          case (_: CreateSet, _: CreateSet) =>

          case (e, o) =>
            issues += PlanningFailure.ConflictingOperation(target, e, o)
        }
      case None =>
    }

    issues
  }



  override def resolve(completedPlan: DodgyPlan): ResolvedSetsPlan = {
    // TODO: further unification with PlanAnalyzer
    val imports = completedPlan
      .topology
      .dependees
      .filterKeys(k => !completedPlan.operations.contains(k))
      .map {
        case (missing, refs) =>
          missing -> ImportDependency(missing, refs.toSet)
      }
      .toMap

    val sortedKeys = Graphs.toposort.cycleBreaking(
      completedPlan.topology.depMap ++ imports.mapValues(v => Set.empty[DIKey]).toMap // 2.13 compat
      , Seq.empty
    )


    val sortedOps = sortedKeys.flatMap(k => completedPlan.operations.get(k).toSeq)
    val out = ResolvedSetsPlan(imports.toMap, sortedOps, completedPlan.issues.toSeq) // 2.13 compat
    out
  }
}



