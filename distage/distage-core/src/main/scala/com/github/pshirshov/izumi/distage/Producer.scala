package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.plan.FinalPlan

trait Producer {
  def produce(dIPlan: FinalPlan): Locator
}
