package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, OperationExecutor, ProvisioningContext}

trait ProviderStrategy {
  def callProvider(context: ProvisioningContext, executor: OperationExecutor, op: WiringOp.CallProvider): Seq[OpResult.NewInstance]
}
