package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningContext}

trait TraitStrategy {
  def makeTrait(context: ProvisioningContext, op: WiringOp.InstantiateTrait): Seq[OpResult]
}
