package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.DIDef
import org.bitbucket.pshirshov.izumi.di.model.plan._


trait Injector {
  def plan(context: DIDef): ReadyPlan

  def produce(dIPlan: ReadyPlan): DIContext
}





