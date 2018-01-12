package org.bitbucket.pshirshov.izumi.di.planning
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.plan.{ExecutableOp, ReadyPlan}

trait SanityChecker {

  def assertSanity(plan: ReadyPlan): Unit

  def assertNoDuplicateOps(ops: Seq[ExecutableOp]): Unit

  def assertNoDuplicateKeys(keys: Seq[DIKey]): Unit
}
