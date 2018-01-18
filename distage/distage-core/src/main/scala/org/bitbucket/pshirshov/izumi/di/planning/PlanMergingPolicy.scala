package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.definition.Binding
import org.bitbucket.pshirshov.izumi.di.model.plan.{DodgyPlan, NextOps}

trait PlanMergingPolicy {
  def extendPlan(currentPlan: DodgyPlan, binding: Binding, currentOp: NextOps): DodgyPlan
}
