package izumi.distage.model.plan

import izumi.distage.model.PlannerInput
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.graphs.DG

case class DIPlan(plan: DG[DIKey, ExecutableOp], input: PlannerInput)
