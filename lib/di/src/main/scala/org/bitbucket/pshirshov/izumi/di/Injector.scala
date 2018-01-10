package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.DIDef
import org.bitbucket.pshirshov.izumi.di.plan._




trait Injector {
  def plan(context: DIDef): DIPlan

  def produce(dIPlan: DIPlan): DIContext
}





