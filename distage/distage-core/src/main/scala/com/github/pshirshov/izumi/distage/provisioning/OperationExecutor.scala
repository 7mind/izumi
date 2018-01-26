package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp

trait OperationExecutor {
  def execute(context: ProvisioningContext, step: ExecutableOp): Seq[OpResult]
}

