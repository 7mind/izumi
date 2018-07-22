package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, SemiPlan, NextOps, OrderedPlan}

trait PlanMergingPolicy {
  def extendPlan(currentPlan: DodgyPlan, binding: Binding, currentOp: NextOps): DodgyPlan

  def finalizePlan(completedPlan: DodgyPlan): SemiPlan

  def reorderOperations(completedPlan: SemiPlan): OrderedPlan
}
