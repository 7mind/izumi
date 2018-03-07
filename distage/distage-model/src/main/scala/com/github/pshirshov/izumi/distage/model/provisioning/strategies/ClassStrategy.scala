package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningContext}

trait ClassStrategy {
  def instantiateClass(context: ProvisioningContext, op: WiringOp.InstantiateClass): Seq[OpResult.NewInstance]

}
