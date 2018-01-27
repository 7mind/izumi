package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningContext}

trait ProviderStrategy {
  def callProvider(context: ProvisioningContext, op: ExecutableOp.WiringOp.CallProvider): Seq[OpResult.NewInstance]
}
