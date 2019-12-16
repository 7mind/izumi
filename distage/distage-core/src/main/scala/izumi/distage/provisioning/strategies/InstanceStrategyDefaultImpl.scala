package izumi.distage.provisioning.strategies

import izumi.distage.model.exceptions.MissingInstanceException
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.strategies.InstanceStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider, WiringExecutor}
import izumi.fundamentals.platform.language.unused

class InstanceStrategyDefaultImpl extends InstanceStrategy {
  def getInstance(context: ProvisioningKeyProvider, @unused executor: WiringExecutor, op: WiringOp.UseInstance): Seq[NewObjectOp] = {
    Seq(NewObjectOp.NewInstance(op.target, op.wiring.instance))
  }

  def getInstance(context: ProvisioningKeyProvider, @unused executor: WiringExecutor, op: WiringOp.ReferenceKey): Seq[NewObjectOp] = {
    context.fetchKey(op.wiring.key, makeByName = false) match {
      case Some(value) =>
        Seq(NewObjectOp.NewInstance(op.target, value))

      case None =>
        throw new MissingInstanceException(s"Cannot find ${op.wiring.key} in the object graph", op.wiring.key)
    }
  }
}
