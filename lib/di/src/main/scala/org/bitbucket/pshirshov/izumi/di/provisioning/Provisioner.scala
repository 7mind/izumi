package org.bitbucket.pshirshov.izumi.di.provisioning

import org.bitbucket.pshirshov.izumi.di.Locator
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.plan.FinalPlan

import scala.collection._

trait Provisioner {
  def provision(dIPlan: FinalPlan, parentContext: Locator): Map[DIKey, Any]
}
