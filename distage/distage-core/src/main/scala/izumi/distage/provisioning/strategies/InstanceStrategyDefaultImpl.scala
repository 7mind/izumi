package izumi.distage.provisioning.strategies

import izumi.distage.model.exceptions.MissingInstanceException
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.strategies.InstanceStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}

class InstanceStrategyDefaultImpl extends InstanceStrategy {
  def getInstance(context: ProvisioningKeyProvider, op: WiringOp.UseInstance): Seq[NewObjectOp] = {
    Seq(NewObjectOp.NewInstance(op.target, op.instanceType, op.wiring.instance))
  }

  def getInstance(context: ProvisioningKeyProvider, op: WiringOp.ReferenceKey): Seq[NewObjectOp] = {
    context.fetchKey(op.wiring.key, makeByName = false) match {
      case Some(value) =>
        Seq(NewObjectOp.UseInstance(op.target, value))

      case None =>
        throw new MissingInstanceException(s"Cannot find ${op.wiring.key} in the object graph", op.wiring.key)
    }
  }
}
