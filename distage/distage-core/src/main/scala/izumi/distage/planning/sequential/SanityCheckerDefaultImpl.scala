package izumi.distage.planning.sequential

import izumi.distage.model.exceptions._
import izumi.distage.model.plan.ExecutableOp.ProxyOp.InitProxy
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
      throw new ForwardRefException(s"Cannot finish the plan, there are forward references: ${reftable.dependees.graph.mkString("\n")}!", reftable)
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
    val (uniqOps, nonUniqueOps) = ops
      .foldLeft((mutable.ArrayBuffer[DIKey](), mutable.HashSet[DIKey]())) {
        case ((unique, nonunique), s: CreateSet) =>
          (unique, nonunique += s.target)
        case ((unique, nonunique), s) =>
          (unique += s.target, nonunique)
      }
    assertNoDuplicateKeys(uniqOps.toSeq ++ nonUniqueOps.toSeq) // 2.13 compat

    val proxyInits = ops.collect { case op: ProxyOp.InitProxy => op}
    val proxies = ops.collect { case op: ProxyOp.MakeProxy => op}
    val proxyInitSources = proxyInits.map(_.target.proxied : DIKey)
    val proxyKeys = proxies.map(_.target : DIKey)

    // every proxy op has matching init op
    val missingProxies = proxyKeys.diff(proxyInitSources).toSet
    if (missingProxies.nonEmpty) {
      throw new MissingRefException(s"BUG: Cannot finish the plan, there are missing MakeProxy operations: $missingProxies!", missingProxies, None)
    }
    // every init op has matching proxy op
    val missingInits = proxyInitSources.diff(proxyKeys).toSet
    if (missingInits.nonEmpty) {
      throw new MissingRefException(s"BUG: Cannot finish the plan, there are missing InitProxy operations: $missingInits!", missingInits, None)
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
