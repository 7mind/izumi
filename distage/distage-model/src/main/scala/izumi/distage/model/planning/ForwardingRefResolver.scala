package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan

trait ForwardingRefResolver {
  def resolve(operations: OrderedPlan): OrderedPlan
}
