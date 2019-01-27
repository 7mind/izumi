package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.MissingInstanceException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.InstanceStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{ContextAssignment, ProvisioningKeyProvider}

class InstanceStrategyDefaultImpl extends InstanceStrategy {
  def getInstance(context: ProvisioningKeyProvider, op: WiringOp.ReferenceInstance): Seq[ContextAssignment] = {
    Seq(ContextAssignment.NewInstance(op.target, op.wiring.instance))
  }

  def getInstance(context: ProvisioningKeyProvider, op: WiringOp.ReferenceKey): Seq[ContextAssignment] = {
    context.fetchKey(op.wiring.key, byName = false) match {
      case Some(value) =>
        Seq(ContextAssignment.NewInstance(op.target, value))

      case None =>
        throw new MissingInstanceException(s"Cannot find ${op.wiring.key} in the object graph", op.wiring.key)
    }
  }
}
