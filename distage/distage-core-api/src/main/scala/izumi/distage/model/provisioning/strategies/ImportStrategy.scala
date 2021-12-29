package izumi.distage.model.provisioning.strategies

import izumi.distage.model.exceptions.interpretation.ProvisionerIssue.MissingImport
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}

trait ImportStrategy {
  def importDependency(context: ProvisioningKeyProvider, op: ImportDependency): Either[MissingImport, Seq[NewObjectOp]]
}
