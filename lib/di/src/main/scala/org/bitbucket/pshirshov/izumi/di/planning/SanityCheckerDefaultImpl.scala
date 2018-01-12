package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.exceptions.{DuplicateKeysException, ForwardRefException, MissingRefException}
import org.bitbucket.pshirshov.izumi.di.model.plan.{ExecutableOp, ReadyPlan}

class SanityCheckerDefaultImpl
(
  protected val planAnalyzer: PlanAnalyzer
)
  extends SanityChecker {
  override def assertSanity(plan: ReadyPlan): Unit = {
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
      throw new MissingRefException(s"Cannot finish the plan, there are missing references: $missingRefs in ${fullRefTable.dependants}!", missingRefs, fullRefTable)
    }

  }


  override def assertNoDuplicateOps(ops: Seq[ExecutableOp]): Unit = {
    assertNoDuplicateKeys(ops.map(_.target))
  }

  override def assertNoDuplicateKeys(keys: Seq[DIKey]): Unit = {
    if (duplicates(keys).nonEmpty) {
      throw new DuplicateKeysException(s"Cannot finish the plan, there are duplicates: $keys!", keys)
    }
  }

  // TODO: quadratic
  private def duplicates(keys: Seq[DIKey]): Seq[DIKey] = keys.map {
    k => (k, keys.count(_ == k))
  }.filter(_._2 > 1).map(_._1)

}
