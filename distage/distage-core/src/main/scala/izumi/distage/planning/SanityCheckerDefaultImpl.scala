package izumi.distage.planning

import izumi.distage.model.exceptions._
import izumi.distage.model.plan.ExecutableOp.{CreateSet, ProxyOp}
import izumi.distage.model.plan.{ExecutableOp, OrderedPlan}
import izumi.distage.model.planning.{PlanAnalyzer, SanityChecker}
import izumi.distage.model.reflection.DIKey

import scala.collection.mutable

class SanityCheckerDefaultImpl(
  protected val planAnalyzer: PlanAnalyzer
) extends SanityChecker {

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
      throw new MissingRefException(
        s"Cannot finish the plan, there are missing references: $missingRefs in ${fullRefTable.dependencies}!",
        missingRefs,
        Some(fullRefTable),
      )
    }

    val missingRoots = plan.declaredRoots.diff(plan.keys)
    if (missingRoots.nonEmpty) {
      throw new MissingRootsException(s"Missing GC roots in final plan, check if there were any conflicts: $missingRoots", missingRoots)
    }
  }

  override def assertNoDuplicateOps(ops: Seq[ExecutableOp]): Unit = {
    val (proxies, single) = ops.partition(_.isInstanceOf[ProxyOp.InitProxy])

    val (uniqOps, nonUniqueOps) = single
      .foldLeft((mutable.ArrayBuffer[DIKey](), mutable.HashSet[DIKey]())) {
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

  private def assertNoDuplicateKeys(keys: Seq[DIKey]): Unit = {
    val dupes = duplicates(keys)
    if (dupes.nonEmpty) {
      throw new DuplicateKeysException(s"Cannot finish the plan, there are duplicates: $dupes!", dupes)
    }
  }

  private def duplicates(keys: Seq[DIKey]): Map[DIKey, Int] = {
    val counted = keys
      .groupBy(k => k)
      .map(t => (t._1, t._2.length))

    counted.filter(_._2 > 1)
  }

}
