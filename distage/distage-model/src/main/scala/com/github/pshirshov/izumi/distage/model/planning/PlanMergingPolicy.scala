package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, OrderedPlan, SemiPlan}

trait PlanMergingPolicy {

  def freeze(completedPlan: DodgyPlan): SemiPlan

}
