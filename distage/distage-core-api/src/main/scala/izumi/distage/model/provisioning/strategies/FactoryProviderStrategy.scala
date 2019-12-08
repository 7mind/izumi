package izumi.distage.model.provisioning.strategies

import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider, WiringExecutor}

trait FactoryProviderStrategy {
  def callFactoryProvider(context: ProvisioningKeyProvider, executor: WiringExecutor, op: WiringOp.CallFactoryProvider): Seq[NewObjectOp]
}
