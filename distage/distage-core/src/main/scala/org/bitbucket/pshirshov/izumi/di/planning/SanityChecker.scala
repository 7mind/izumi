package org.bitbucket.pshirshov.izumi.di.planning
import org.bitbucket.pshirshov.izumi.di.model.plan.{DodgyPlan, ExecutableOp, FinalPlan, NextOps}

trait SanityChecker {

  def assertSanity(plan: FinalPlan): Unit

  def assertNoDuplicateOps(ops: Seq[ExecutableOp]): Unit
  def assertNoDuplicateOps(plan: DodgyPlan): Unit = assertNoDuplicateOps(plan.statements)
  def assertNoDuplicateOps(nextOps: NextOps): Unit = assertNoDuplicateOps(nextOps.flatten)
}
