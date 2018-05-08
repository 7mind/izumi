package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp._
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning.PlanMergingPolicy
import com.github.pshirshov.izumi.distage.model.reflection
import com.github.pshirshov.izumi.distage.model.reflection.universe
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.language.implicitConversions

// TODO: move unify graph ops with PlanAnalyzer
class PlanMergingPolicyDefaultImpl() extends PlanMergingPolicy {
  override def extendPlan(currentPlan: DodgyPlan, binding: Binding, currentOp: NextOps): DodgyPlan = {
    currentOp.provisions.foreach {
      op =>
        val target = op.target

        val issues = findIssues(currentPlan, op)
        if (issues.isEmpty) {
          val opDeps: Set[universe.RuntimeDIUniverse.DIKey] = getTransitiveDependencies(currentPlan, op)
          currentPlan.operations.put(target, op)
          registerDep(currentPlan, target, opDeps)
        } else {
          currentPlan.issues ++= issues
        }
    }

    currentOp.sets.values.foreach {
      op =>
        val target = op.target
        val opDeps: Set[universe.RuntimeDIUniverse.DIKey] = getTransitiveDependencies(currentPlan, op)

        val old = currentPlan.operations.getOrElseUpdate(target, op)
        val merged = op.copy(members = old.asInstanceOf[CreateSet].members ++ op.members)

        currentPlan.operations.put(target, merged)
        registerDep(currentPlan, target, opDeps)
    }

    currentPlan
  }

  private def getTransitiveDependencies(plan: DodgyPlan, op: InstantiationOp): Set[reflection.universe.RuntimeDIUniverse.DIKey] = {
    val opDeps = requirements(op).flatMap {
      req =>
        plan.dependencies.getOrElse(req, mutable.Set.empty[DIKey]) + req
    }
    opDeps
  }

  private def registerDep(plan: DodgyPlan, target: RuntimeDIUniverse.DIKey, opDeps: Set[RuntimeDIUniverse.DIKey]): Unit = {
    plan.dependencies.getOrElseUpdate(target, mutable.Set.empty[DIKey])
    plan.dependees.getOrElseUpdate(target, mutable.Set.empty[DIKey])
    opDeps.foreach {
      opDep =>
        plan.dependees.addBinding(opDep, target)
        plan.dependencies.addBinding(target, opDep)
    }
  }

  private def findIssues(currentPlan: DodgyPlan, op: InstantiationOp) = {
    val target = op.target

    val issues = mutable.ArrayBuffer.empty[PlanningFailure]

    if (currentPlan.dependencies.contains(op.target)) {
      issues += PlanningFailure.UnsolvableConflict(target, Seq.empty)
    } else if (currentPlan.operations.contains(target)) {
      issues += PlanningFailure.UnsolvableConflict(target, Seq.empty)
    }

    issues
  }

  @tailrec
  private def topoSort(toPreds: Map[DIKey, Set[DIKey]], done: Seq[DIKey]): Seq[DIKey] = {
    val (noPreds, hasPreds) = toPreds.partition { _._2.isEmpty }

    if (noPreds.isEmpty) {
      if (hasPreds.isEmpty) {
        done
      } else { // circular dependency, trying to break it by removing head
        val found = Seq(hasPreds.head._1)
        topoSort(hasPreds.tail.mapValues { _ -- found }, done ++ found)
      }
    } else {
      val found = noPreds.keys
      topoSort(hasPreds.mapValues { _ -- found }, done ++ found)
    }
  }

  override def resolve(completedPlan: DodgyPlan): ResolvedSetsPlan = {
    val imports = completedPlan
      .dependees
      .filterKeys(k => !completedPlan.operations.contains(k))
      .map {
        case (missing, refs) =>
          missing -> ImportDependency(missing, refs.toSet)
      }

    val ops = completedPlan.dependencies.mapValues(_.toSet).toMap

    val sortedKeys = topoSort(ops ++ imports.mapValues(v => Set.empty[DIKey]), Seq.empty)
    val sortedOps = sortedKeys.flatMap(k => completedPlan.operations.get(k).toSeq)
    val out = ResolvedSetsPlan(imports.toMap, sortedOps, completedPlan.issues)
    out
  }

  private def requirements(op: InstantiationOp): Set[DIKey] = {
    op match {
      case w: WiringOp =>
        w.wiring.associations.map(_.wireWith).toSet

      case c: CreateSet =>
        c.members

      case p: ProxyOp =>
        throw new DIException(s"Unexpected op: $p", null)

      case _ =>
        Set.empty
    }
  }
}
