package org.bitbucket.pshirshov.izumi.distage

import org.bitbucket.pshirshov.izumi.distage.model.plan.FinalPlan

trait Producer {
  def produce(dIPlan: FinalPlan): Locator
}
