package izumi.distage.model.provisioning.strategies

import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}

trait InstanceStrategy {
  def getInstance(context: ProvisioningKeyProvider, op: WiringOp.UseInstance): Seq[NewObjectOp]
  def getInstance(context: ProvisioningKeyProvider, op: WiringOp.ReferenceKey): Seq[NewObjectOp]
}
