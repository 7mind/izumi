package org.bitbucket.pshirshov.izumi.distage.provisioning.strategies

import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}

trait TraitStrategy {
  def makeTrait(context: ProvisioningContext, t: WiringOp.InstantiateTrait): Seq[OpResult]
}
