package org.bitbucket.pshirshov.izumi.distage.planning

import org.bitbucket.pshirshov.izumi.distage.definition.Binding
import org.bitbucket.pshirshov.izumi.distage.model.plan.{DodgyPlan, NextOps}

trait PlanMergingPolicy {
  def extendPlan(currentPlan: DodgyPlan, binding: Binding, currentOp: NextOps): DodgyPlan
}
