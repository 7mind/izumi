package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.FinalPlan

trait ForwardingRefResolver {
  def resolve(operations: FinalPlan): FinalPlan
}
