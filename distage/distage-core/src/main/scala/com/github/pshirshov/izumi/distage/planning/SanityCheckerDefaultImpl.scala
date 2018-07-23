package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.exceptions._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{CreateSet, ProxyOp}
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, ExecutableOp, OrderedPlan}
import com.github.pshirshov.izumi.distage.model.planning.{PlanAnalyzer, SanityChecker}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

import scala.collection.mutable

class SanityCheckerDefaultImpl
(
  protected val planAnalyzer: PlanAnalyzer
)
  extends SanityChecker {

  override def assertStepSane(plan: DodgyPlan): Unit = {
//    plan.topology.dependencies.foreach {
//      kv =>
//        kv._2.foreach {
//          dep =>
//            if (!plan.topology.dependees(dep).contains(kv._1)) {
//              throw new SanityCheckFailedException(s"Sanity check failed: deptables are asymmetric!")
//            }
//        }
//    }
//    plan.topology.dependees.foreach {
//      kv =>
//        kv._2.foreach {
//          dep =>
//            if (!plan.topology.dependencies(dep).contains(kv._1)) {
//              throw new SanityCheckFailedException(s"Sanity check failed: deptables are asymmetric!")
//            }
//        }
//    }
  }

  override def assertFinalPlanSane(plan: OrderedPlan): Unit = {
    assertNoDuplicateOps(plan.steps)

    val reftable = planAnalyzer.topologyFwdRefs(plan.steps)
    if (reftable.dependees.graph.nonEmpty) {
      throw new ForwardRefException(s"Cannot finish the plan, there are forward references: ${reftable.dependees}!", reftable)
    }

    val fullRefTable = planAnalyzer.topology(plan.steps)

    val allAvailableRefs = fullRefTable.dependencies.graph.keySet
    val fullDependenciesSet = fullRefTable.dependencies.graph.flatMap(_._2).toSet
    val missingRefs = fullDependenciesSet -- allAvailableRefs
    if (missingRefs.nonEmpty) {
      throw new MissingRefException(s"Cannot finish the plan, there are missing references: $missingRefs in ${fullRefTable.dependencies}!", missingRefs, Some(fullRefTable))
    }

  }


  override def assertNoDuplicateOps(ops: Seq[ExecutableOp]): Unit = {
    val (proxies, single) = ops.partition(_.isInstanceOf[ProxyOp.InitProxy])

    val (uniqOps, nonUniqueOps) = single
      .foldLeft((mutable.ArrayBuffer[RuntimeDIUniverse.DIKey](), mutable.HashSet[RuntimeDIUniverse.DIKey]())) {
        case ((unique, nonunique), s: CreateSet) =>
          (unique, nonunique += s.target)
        case ((unique, nonunique), s) =>
          (unique += s.target, nonunique)
      }

    val proxyKeys = proxies.map(_.target)

    assertNoDuplicateKeys(uniqOps.toSeq ++ nonUniqueOps.toSeq) // 2.13 compat
    assertNoDuplicateKeys(proxyKeys)

    val missingProxies = proxyKeys.toSet -- uniqOps.toSet
    if (missingProxies.nonEmpty) {
      throw new MissingRefException(s"Cannot finish the plan, there are missing proxy refs: $missingProxies!", missingProxies, None)

    }
  }

  private def assertNoDuplicateKeys(keys: Seq[RuntimeDIUniverse.DIKey]): Unit = {
    val dupes = duplicates(keys)
    if (dupes.nonEmpty) {
      throw new DuplicateKeysException(s"Cannot finish the plan, there are duplicates: $dupes!", dupes)
    }
  }

  private def duplicates(keys: Seq[RuntimeDIUniverse.DIKey]): Map[RuntimeDIUniverse.DIKey, Int] = {
    val counted = keys
      .groupBy(k => k)
      .map(t => (t._1, t._2.length))

    counted.filter(_._2 > 1)
  }

}
