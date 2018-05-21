package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp

trait OperationExecutor {
  def execute(context: ProvisioningKeyProvider, step: ExecutableOp): Seq[OpResult]
}

