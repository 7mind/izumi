package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.MissingInstanceException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.InstanceStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}

class InstanceStrategyDefaultImpl extends InstanceStrategy {
  def getInstance(context: ProvisioningKeyProvider, op: WiringOp.ReferenceInstance): Seq[NewObjectOp] = {
    Seq(NewObjectOp.NewInstance(op.target, op.wiring.instance))
  }

  def getInstance(context: ProvisioningKeyProvider, op: WiringOp.ReferenceKey): Seq[NewObjectOp] = {
    context.fetchKey(op.wiring.key, byName = false) match {
      case Some(value) =>
        Seq(NewObjectOp.NewInstance(op.target, value))

      case None =>
        throw new MissingInstanceException(s"Cannot find ${op.wiring.key} in the object graph", op.wiring.key)
    }
  }
}
