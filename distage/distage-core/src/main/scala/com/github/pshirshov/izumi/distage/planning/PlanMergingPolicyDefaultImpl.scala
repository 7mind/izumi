package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.exceptions.{SanityCheckFailedException, UntranslatablePlanException}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp._
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning.{PlanAnalyzer, PlanMergingPolicy}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
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


  override def finalizePlan(completedPlan: DodgyPlan): SemiPlan = {
    if (completedPlan.issues.nonEmpty) {
      throw new UntranslatablePlanException(s"Cannot translate untranslatable (with default policy):\n${completedPlan.issues.mkString("\n")}", completedPlan.issues)
    }

    // TODO: here we may check the plan for conflicts

    // it's not neccessary to sort the plan at this stage, it's gonna happen after GC
    val imports = findImports(completedPlan.topology.immutable, completedPlan.operations.toMap)
    SemiPlan(completedPlan.definition, (imports.values ++ completedPlan.operations.values).toVector)
  }

  override def reorderOperations(completedPlan: SemiPlan): OrderedPlan = {
    // TODO: further unification with PlanAnalyzer
    val index = completedPlan.index
    val topology = analyzer.topoBuild(completedPlan.steps)
    val sortedKeys = Graphs.toposort.cycleBreaking(
      topology.dependencies
      , Seq.empty
    )

    val sortedOps = sortedKeys.flatMap(k => index.get(k).toSeq)
    OrderedPlan(completedPlan.definition, sortedOps.toVector, topology)
  }


  private def findImports(topology: PlanTopology, index: Map[RuntimeDIUniverse.DIKey, InstantiationOp]) = {
    val imports = topology
      .dependees
      .filterKeys(k => !index.contains(k))
      .map {
        case (missing, refs) =>
          missing -> ImportDependency(missing, refs.toSet, None)
      }
      .toMap
    imports
  }
}



