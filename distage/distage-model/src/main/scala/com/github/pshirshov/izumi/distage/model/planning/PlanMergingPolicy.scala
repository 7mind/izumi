package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan, NextOps}

trait PlanMergingPolicy {
  def extendPlan(currentPlan: DodgyPlan, binding: Binding, currentOp: NextOps): DodgyPlan

  def reorderOperations(completedPlan: DodgyPlan): FinalPlan

  def reorderOperations(completedPlan: FinalPlan): FinalPlan
}
