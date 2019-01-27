package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import com.github.pshirshov.izumi.distage.model.provisioning.FailedProvision

trait Producer {
  def produce(plan: OrderedPlan): Either[FailedProvision, Locator]

  def produceUnsafe(plan: OrderedPlan): Locator = {
    produce(plan).throwIfFailure()
  }
}
