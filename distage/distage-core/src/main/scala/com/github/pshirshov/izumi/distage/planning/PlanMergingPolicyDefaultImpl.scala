package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.SetOp.AddToSet
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp._
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning.{PlanAnalyzer, PlanMergingPolicy}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

import scala.collection.mutable

class PlanMergingPolicyDefaultImpl(
                                    protected val planAnalyzer: PlanAnalyzer
                                  ) extends PlanMergingPolicy {

  def extendPlan(currentPlan: DodgyPlan, binding: Binding, currentOp: NextOps): DodgyPlan = {
    val oldImports = currentPlan.imports.keySet

    val newProvisionKeys = newKeys(currentOp)

    val safeNewProvisions = if (oldImports.intersect(newProvisionKeys).isEmpty) {
      currentPlan.steps ++ currentOp.provisions
    } else {
      val (independent, dependent) = split(currentPlan.steps, currentOp)
      independent ++ currentOp.provisions ++ dependent
    }

    val newImports = computeNewImports(currentPlan, currentOp)
    val newSets = currentPlan.sets ++ currentOp.sets


    val newPlan = DodgyPlan(
      newImports
      , newSets
      , currentPlan.proxies
      , safeNewProvisions
      , currentPlan.issues
    )

    val issuesMap = newPlan
      .statements
      .groupBy(_.target)
      .filter(_._2.lengthCompare(1) > 0)
      .filterNot(_._2.forall(_.isInstanceOf[SetOp]))

    val issues: Seq[PlanningFailure] = issuesMap
      .map {
        case (key, values) =>
          PlanningFailure.UnsolvableConflict(key, values)
      }
      .toSeq

    newPlan.copy(issues = newPlan.issues ++ issues, steps = newPlan.steps.filterNot(step => issuesMap.contains(step.target)))
  }


  private def split(steps: Seq[ExecutableOp.InstantiationOp], currentOp: NextOps): (Seq[ExecutableOp.InstantiationOp], Seq[ExecutableOp.InstantiationOp]) = {
    val newProvisionKeys = newKeys(currentOp)

    val left = mutable.ArrayBuffer[ExecutableOp.InstantiationOp]()
    val right = mutable.ArrayBuffer[ExecutableOp.InstantiationOp]()
    val rightSet = mutable.LinkedHashSet[RuntimeDIUniverse.DIKey]()


    steps.foreach {
      step =>
        val required = requirements(step)

        val toRight = required.intersect(newProvisionKeys).nonEmpty || required.intersect(rightSet).nonEmpty

        if (toRight) {
          rightSet += step.target
          right += step
        } else {
          left += step
        }

    }

    (left, right)
  }

  private def requirements(op: InstantiationOp): Set[RuntimeDIUniverse.DIKey] = {
    op match {
      case w: WiringOp =>
        w.wiring.associations.map(_.wireWith).toSet

      case s: AddToSet =>
        Set(s.element)

      case p: ProxyOp =>
        throw new DIException(s"Unexpected op: $p", null)

      case _ =>
        Set.empty
    }
  }
  private def computeNewImports(currentPlan: DodgyPlan, currentOp: NextOps) = {
    val newProvisionKeys = newKeys(currentOp)

    val currentImportsMap = currentPlan.imports
      .values
      .filterNot(imp => newProvisionKeys.contains(imp.target))
      .map(imp => (imp.target, imp))

    val newImportsMap = currentOp.imports
      .filterNot(imp => newProvisionKeys.contains(imp.target))
      .map(imp => (imp.target, imp))

    val newImports = newImportsMap.foldLeft(currentImportsMap.toMap) {
      case (acc, (target, op)) =>
        val importOp = acc.getOrElse(target, op)
        acc.updated(target, ImportDependency(target, importOp.references ++ op.references))
    }
    newImports
  }

  private def newKeys(currentOp: NextOps): Set[RuntimeDIUniverse.DIKey] = {
    currentOp
      .provisions
      .map(op => op.target)
      .toSet
  }
}
