package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, ExecutableOp, FinalPlan, NextOps}

trait SanityChecker {

  def assertSanity(plan: FinalPlan): Unit

  def assertNoDuplicateOps(ops: Seq[ExecutableOp]): Unit
  def assertNoDuplicateOps(plan: DodgyPlan): Unit = assertNoDuplicateOps(plan.statements)
  def assertNoDuplicateOps(nextOps: NextOps): Unit = assertNoDuplicateOps(nextOps.flatten)
}
