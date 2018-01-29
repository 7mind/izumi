package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningContext}

trait ClassStrategy {
  def instantiateClass(context: ProvisioningContext, op: ExecutableOp.WiringOp.InstantiateClass): Seq[OpResult.NewInstance]

}
