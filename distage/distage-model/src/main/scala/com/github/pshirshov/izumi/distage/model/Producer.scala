package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan

trait Producer {
  def produce(plan: OrderedPlan): Locator
}
