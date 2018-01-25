package org.bitbucket.pshirshov.izumi.distage.provisioning.strategies

import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}

trait ImportStrategy {
  def importDependency(context: ProvisioningContext, op: ExecutableOp.ImportDependency): Seq[OpResult]

}
