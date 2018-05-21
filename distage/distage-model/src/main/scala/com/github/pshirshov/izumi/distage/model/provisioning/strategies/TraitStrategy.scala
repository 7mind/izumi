package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningKeyProvider}

trait TraitStrategy {
  def makeTrait(context: ProvisioningKeyProvider, op: WiringOp.InstantiateTrait): Seq[OpResult]
}
