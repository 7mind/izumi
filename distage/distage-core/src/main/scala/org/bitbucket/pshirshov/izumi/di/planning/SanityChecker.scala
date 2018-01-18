package org.bitbucket.pshirshov.izumi.di.planning
import org.bitbucket.pshirshov.izumi.di.model.plan.{ExecutableOp, FinalPlan}

trait SanityChecker {

  def assertSanity(plan: FinalPlan): Unit

  def assertNoDuplicateOps(ops: Seq[ExecutableOp]): Unit
}
