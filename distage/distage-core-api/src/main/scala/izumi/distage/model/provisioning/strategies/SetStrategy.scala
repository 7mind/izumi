package izumi.distage.model.provisioning.strategies

import izumi.distage.model.plan.ExecutableOp.CreateSet
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}

trait SetStrategy {
  def makeSet(context: ProvisioningKeyProvider, op: CreateSet): Seq[NewObjectOp]
}
