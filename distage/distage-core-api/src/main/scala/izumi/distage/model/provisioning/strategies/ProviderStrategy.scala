package izumi.distage.model.provisioning.strategies

import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider, WiringExecutor}

trait ProviderStrategy {
  def callProvider(context: ProvisioningKeyProvider, executor: WiringExecutor, op: WiringOp.CallProvider): Seq[NewObjectOp]
}
