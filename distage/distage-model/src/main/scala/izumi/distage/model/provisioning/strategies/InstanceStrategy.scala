package izumi.distage.model.provisioning.strategies

import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider, WiringExecutor}

trait InstanceStrategy {
  def getInstance(context: ProvisioningKeyProvider, executor: WiringExecutor, op: WiringOp.UseInstance): Seq[NewObjectOp]
  def getInstance(context: ProvisioningKeyProvider, executor: WiringExecutor, op: WiringOp.ReferenceKey): Seq[NewObjectOp]
}
