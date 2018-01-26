package org.bitbucket.pshirshov.izumi.distage.provisioning

import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp

trait OperationExecutor {
  def execute(context: ProvisioningContext, step: ExecutableOp): Seq[OpResult]
}

