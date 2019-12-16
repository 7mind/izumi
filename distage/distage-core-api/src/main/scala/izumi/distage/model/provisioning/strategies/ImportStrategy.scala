package izumi.distage.model.provisioning.strategies

import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider, WiringExecutor}

trait ImportStrategy {
  def importDependency(context: ProvisioningKeyProvider, executor: WiringExecutor, op: ImportDependency): Seq[NewObjectOp]
}
