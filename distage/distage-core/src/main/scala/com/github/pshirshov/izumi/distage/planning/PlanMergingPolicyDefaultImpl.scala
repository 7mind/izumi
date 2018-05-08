package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp._
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning.{PlanAnalyzer, PlanMergingPolicy}
import com.github.pshirshov.izumi.distage.model.reflection
import com.github.pshirshov.izumi.distage.model.reflection.universe
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.language.implicitConversions

class PlanMergingPolicyDefaultImpl(
                                    protected val planAnalyzer: PlanAnalyzer
                                  ) extends PlanMergingPolicy {
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

    currentPlan.dependencies.foreach {
      kv =>
        kv._2.foreach {
          dep =>
            assert(currentPlan.dependees(dep).contains(kv._1))
        }
    }
    currentPlan.dependees.foreach {
      kv =>
        kv._2.foreach {
          dep =>
            assert(currentPlan.dependencies(dep).contains(kv._1))
        }
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
  private def tsort(toPreds: Map[DIKey, Set[DIKey]], done: Seq[DIKey]): Seq[DIKey] = {
    val (noPreds, hasPreds) = toPreds.partition { _._2.isEmpty }

    if (noPreds.isEmpty) {
      if (hasPreds.isEmpty) {
        done
      } else { // circular dependency, trying to break it by removing head
        val found = Seq(hasPreds.head._1)
        tsort(hasPreds.tail.mapValues { _ -- found }, done ++ found)
      }
    } else {
      val found = noPreds.keys
      tsort(hasPreds.mapValues { _ -- found }, done ++ found)
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

    val sortedKeys = tsort(ops ++ imports.mapValues(v => Set.empty[DIKey]), Seq.empty)
    val sortedOps = sortedKeys.flatMap(k => completedPlan.operations.get(k).toSeq)
    val out = ResolvedSetsPlan(imports.toMap, sortedOps, completedPlan.issues)

//    println("==================")
//    println(out.statements.mkString("\n"))
//    println("==================")
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

//class PlanMergingPolicyDefaultImpl1(
//                                    protected val planAnalyzer: PlanAnalyzer
//                                  ) extends PlanMergingPolicy {
//
//  override def resolve(completedPlan: DodgyPlan): ResolvedSetsPlan = {
//     ResolvedSetsPlan(completedPlan.imports, completedPlan.steps, completedPlan.issues)
//  }
//
//  def extendPlan(currentPlan: DodgyPlan, binding: Binding, currentOp: NextOps): DodgyPlan = {
//    val newSets = computeNewSets(currentPlan, currentOp)
//
//    val newProvisionKeys = newKeys(currentOp)
//
//    val (satisfied, _) = {
//      val allKeys = currentPlan.steps.map(_.target).toSet ++ newProvisionKeys
//      newSets.partition(s => s.members.diff(allKeys).isEmpty)
//    }
//
//    val satisfiedKeys = satisfied.map(_.target)
//    val currentSteps = currentPlan.steps.filterNot(s => satisfiedKeys.contains(s.target))
//    val newProvisions = currentOp.provisions ++ satisfied
//    val fullNewProvisionKeys = newProvisionKeys ++ satisfied.map(_.target)
//
//
//    val oldImports = currentPlan.imports.keySet
//
//
//    val safeNewProvisions = if (oldImports.intersect(fullNewProvisionKeys).isEmpty) {
//      currentSteps ++ newProvisions
//    } else {
//      val (independent, dependent) = split(currentSteps, fullNewProvisionKeys)
//      independent ++ newProvisions ++ dependent
//    }
//
//    val newImports = computeNewImports(currentPlan, currentOp.imports, fullNewProvisionKeys)
//
//
//    val newPlan = DodgyPlan(
//      newImports
//      , newSets
//      , safeNewProvisions
//      , currentPlan.issues
//    )
//
//    val issues = findIssues(newPlan.imports.values.toSeq ++ newPlan.steps)
//    newPlan.copy(issues = newPlan.issues ++ issues.issues, steps = newPlan.steps.filterNot(step => issues.issuesMap.contains(step.target)))
//  }
//
//  private def computeNewSets(currentPlan: DodgyPlan, currentOp: NextOps): Set[CreateSet] = {
//    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
//    val newSets = (currentPlan.sets.map(s => s.target -> s).toSeq ++ currentOp.sets.toSeq).toMultimap
//    val mergedSets = newSets.map {
//      case (target, sets) =>
//        sets.tail.foldLeft(sets.head) {
//          case (acc, set) =>
//            assert(acc.tpe == set.tpe && acc.target == set.target && set.target == target)
//            acc.copy(members = acc.members ++ set.members)
//        }
//    }
//    mergedSets.toSet
//  }
//
//  case class Issues(issuesMap: Map[universe.RuntimeDIUniverse.DIKey, Seq[ExecutableOp]]) {
//    val issues: Seq[PlanningFailure] = issuesMap
//      .map {
//        case (key, values) =>
//          PlanningFailure.UnsolvableConflict(key, values)
//      }
//      .toSeq
//  }
//
//  private def findIssues(ops: Seq[ExecutableOp]) = {
//    val issuesMap =
//      ops.groupBy(_.target)
//        .filter(_._2.lengthCompare(1) > 0)
//        .filterNot(_._2.forall(_.isInstanceOf[CreateSet]))
//
//    Issues(issuesMap)
//  }
//
//  private def split(steps: Seq[ExecutableOp.InstantiationOp], newKeys: Set[RuntimeDIUniverse.DIKey]): (Seq[ExecutableOp.InstantiationOp], Seq[ExecutableOp.InstantiationOp]) = {
//    val left = mutable.ArrayBuffer[ExecutableOp.InstantiationOp]()
//    val right = mutable.ArrayBuffer[ExecutableOp.InstantiationOp]()
//    val rightSet = mutable.LinkedHashSet[RuntimeDIUniverse.DIKey]()
//
//    steps.foreach {
//      step =>
//        val required = requirements(step)
//
//        val toRight = required.intersect(newKeys).nonEmpty || required.intersect(rightSet).nonEmpty
//
//        if (toRight) {
//          rightSet += step.target
//          right += step
//        } else {
//          left += step
//        }
//
//    }
//
//    (left, right)
//  }
//
//  private def requirements(op: InstantiationOp): Set[RuntimeDIUniverse.DIKey] = {
//    op match {
//      case w: WiringOp =>
//        w.wiring.associations.map(_.wireWith).toSet
//
//      case c: CreateSet =>
//        c.members
//
//      case p: ProxyOp =>
//        throw new DIException(s"Unexpected op: $p", null)
//
//      case _ =>
//        Set.empty
//    }
//  }
//
//  private def computeNewImports(currentPlan: DodgyPlan, _newImports: Set[ExecutableOp.ImportDependency], newKeys: Set[RuntimeDIUniverse.DIKey]) = {
//    //val newProvisionKeys = newKeys(currentOp)
//
//    val currentImportsMap = currentPlan.imports
//      .values
//      .filterNot(imp => newKeys.contains(imp.target))
//      .map(imp => (imp.target, imp))
//
//    val newImportsMap = _newImports
//      .filterNot(imp => newKeys.contains(imp.target))
//      .map(imp => (imp.target, imp))
//
//    val newImports = newImportsMap.foldLeft(currentImportsMap.toMap) {
//      case (acc, (target, op)) =>
//        val importOp = acc.getOrElse(target, op)
//        acc.updated(target, ImportDependency(target, importOp.references ++ op.references))
//    }
//    newImports
//  }
//
//  private def newKeys(currentOp: NextOps): Set[RuntimeDIUniverse.DIKey] = {
//    val all = currentOp.provisions.map(op => op.target)
//    all.toSet
//  }
//}
