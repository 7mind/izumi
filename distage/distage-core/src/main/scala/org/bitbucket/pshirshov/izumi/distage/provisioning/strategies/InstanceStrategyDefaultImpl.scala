package org.bitbucket.pshirshov.izumi.distage.provisioning.strategies

import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}

class InstanceStrategyDefaultImpl extends InstanceStrategy {
  def getInstance(context: ProvisioningContext, op: ExecutableOp.WiringOp.ReferenceInstance): Seq[OpResult] = {
    Seq(OpResult.NewInstance(op.target, op.wiring.instance))
  }
}

object InstanceStrategyDefaultImpl {
  final val instance = new InstanceStrategyDefaultImpl()
}