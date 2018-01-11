package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.plan.ReadyPlan

trait Injector {
  def produce(dIPlan: ReadyPlan): DIContext
}
