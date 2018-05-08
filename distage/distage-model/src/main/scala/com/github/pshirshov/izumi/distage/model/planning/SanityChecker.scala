package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, ExecutableOp, FinalPlan, NextOps}

trait SanityChecker {

  def assertFinalPlanSane(plan: FinalPlan): Unit

  def assertStepSane(plan: DodgyPlan): Unit

  def assertNoDuplicateOps(ops: Seq[ExecutableOp]): Unit

  def assertProvisionsSane(nextOps: NextOps): Unit = {
    val allOps = nextOps.provisions ++ nextOps.sets.values
    assertNoDuplicateOps(allOps)
  }
}
