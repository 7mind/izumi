package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.plan.FinalPlan

trait Producer {
  def produce(dIPlan: FinalPlan): Locator
}
