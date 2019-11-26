package izumi.distage.model.provisioning.strategies

import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}

trait ClassStrategy {
  def instantiateClass(context: ProvisioningKeyProvider, op: WiringOp.InstantiateClass): Seq[NewObjectOp.NewInstance]
}
