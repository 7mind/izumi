package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan.DodgyPlan

trait ForwardingRefResolver {
  def resolve(steps: DodgyPlan): DodgyPlan
}