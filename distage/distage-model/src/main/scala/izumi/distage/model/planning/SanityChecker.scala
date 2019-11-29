package izumi.distage.model.planning

import izumi.distage.model.plan._
import izumi.distage.model.plan.initial.NextOps

trait SanityChecker {

  def assertFinalPlanSane(plan: OrderedPlan): Unit

  def assertNoDuplicateOps(ops: Seq[ExecutableOp]): Unit

  def assertProvisionsSane(nextOps: NextOps): Unit = {
    val allOps = nextOps.provisions ++ nextOps.sets.values
    assertNoDuplicateOps(allOps)
  }
}
