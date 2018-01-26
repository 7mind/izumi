package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}

trait CustomStrategy {
  def handle(context: ProvisioningContext, op: ExecutableOp.CustomOp): Seq[OpResult]

}
