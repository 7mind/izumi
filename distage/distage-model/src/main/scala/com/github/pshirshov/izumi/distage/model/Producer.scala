package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.plan.FinalPlan

trait Producer {
  def produce(diPlan: FinalPlan): Locator
}
