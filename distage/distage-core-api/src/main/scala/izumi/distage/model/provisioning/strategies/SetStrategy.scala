package izumi.distage.model.provisioning.strategies

import izumi.distage.model.plan.ExecutableOp.CreateSet
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider, WiringExecutor}

trait SetStrategy {
  def makeSet(context: ProvisioningKeyProvider, executor: WiringExecutor, op: CreateSet): Seq[NewObjectOp]
}
