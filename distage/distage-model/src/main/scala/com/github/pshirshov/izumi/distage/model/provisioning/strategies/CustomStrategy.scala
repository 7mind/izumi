package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.CustomOp
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningContext}

trait CustomStrategy {
  def handle(context: ProvisioningContext, op: CustomOp): Seq[OpResult]

}
