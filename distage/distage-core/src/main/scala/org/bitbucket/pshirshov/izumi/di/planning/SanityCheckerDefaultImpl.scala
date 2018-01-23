package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.exceptions.{DuplicateKeysException, ForwardRefException, MissingRefException}
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.{ProxyOp, SetOp}
import org.bitbucket.pshirshov.izumi.di.model.plan.{ExecutableOp, FinalPlan}

import scala.collection.mutable

class SanityCheckerDefaultImpl
(
  protected val planAnalyzer: PlanAnalyzer
)
  extends SanityChecker {
  override def assertSanity(plan: FinalPlan): Unit = {
    assertNoDuplicateOps(plan.steps)

    val reftable = planAnalyzer.computeFwdRefTable(plan.steps.toStream)
    if (reftable.dependants.nonEmpty) {
      throw new ForwardRefException(s"Cannot finish the plan, there are forward references: ${reftable.dependants}!", reftable)
    }

    val fullRefTable = planAnalyzer.computeFullRefTable(plan.steps.toStream)

    val allAvailableRefs = fullRefTable.dependencies.keySet
    val fullDependenciesSet = fullRefTable.dependencies.flatMap(_._2).toSet
    val missingRefs = fullDependenciesSet -- allAvailableRefs
    if (missingRefs.nonEmpty) {
      throw new MissingRefException(s"Cannot finish the plan, there are missing references: $missingRefs in ${fullRefTable.dependants}!", missingRefs, Some(fullRefTable))
    }

  }


  override def assertNoDuplicateOps(ops: Seq[ExecutableOp]): Unit = {
    val (proxies, single) = ops.partition(_.isInstanceOf[ProxyOp.InitProxy])

    val (uniqOps, nonUniqueOps) = single
      .foldLeft((mutable.ArrayBuffer[DIKey](), mutable.HashSet[DIKey]())) {
        case ((unique, nonunique), s: SetOp) =>
          (unique, nonunique += s.target)
        case ((unique, nonunique), s) =>
          (unique += s.target, nonunique)
      }

    val proxyKeys = proxies.map(_.target)

    assertNoDuplicateKeys(uniqOps ++ nonUniqueOps.toSeq)
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
