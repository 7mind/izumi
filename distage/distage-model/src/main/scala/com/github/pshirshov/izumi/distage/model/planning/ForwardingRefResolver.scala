package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.DodgyPlan

trait ForwardingRefResolver {
  def resolve(steps: DodgyPlan): DodgyPlan
}