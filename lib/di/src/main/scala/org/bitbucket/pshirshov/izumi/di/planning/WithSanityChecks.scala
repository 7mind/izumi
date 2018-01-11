package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.exceptions.{DuplicateKeysException, ForwardRefException}
import org.bitbucket.pshirshov.izumi.di.model.plan.{ExecutableOp, ReadyPlan}

trait WithSanityChecks
  extends WithPlanAnalysis {
  protected def assertSanity(plan: ReadyPlan): Unit = {
    assertNoDuplicateOps(plan.getPlan)

    val reftable = computeFwdRefTable(plan)
    if (reftable.dependants.nonEmpty) {
      throw new ForwardRefException(s"Cannot finish the plan, there are forward references: ${reftable.dependants}!", reftable)
    }

    // TODO: make sure circular deps are gone
  }


  protected def assertNoDuplicateOps(ops: Seq[ExecutableOp]): Unit = {
    assertNoDuplicateKeys(ops.map(_.target))
  }

  protected def assertNoDuplicateKeys(keys: Seq[DIKey]): Unit = {
    if (duplicates(keys).nonEmpty) {
      throw new DuplicateKeysException(s"Cannot finish the plan, there are duplicates: $keys!", keys)
    }
  }

  // TODO: quadratic
  private def duplicates(keys: Seq[DIKey]): Seq[DIKey] = keys.map {
    k => (k, keys.count(_ == k))
  }.filter(_._2 > 1).map(_._1)

}
