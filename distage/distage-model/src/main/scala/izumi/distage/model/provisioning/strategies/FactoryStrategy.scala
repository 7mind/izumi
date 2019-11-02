package izumi.distage.model.provisioning.strategies

import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider, WiringExecutor}

trait FactoryStrategy {
  def makeFactory(context: ProvisioningKeyProvider, executor: WiringExecutor, op: WiringOp.InstantiateFactory): Seq[NewObjectOp]
}

