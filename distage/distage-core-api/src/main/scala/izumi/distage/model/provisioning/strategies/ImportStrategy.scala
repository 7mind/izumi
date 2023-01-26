package izumi.distage.model.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue.MissingImport
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.distage.model.plan.Plan
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}

trait ImportStrategy {
  def importDependency(context: ProvisioningKeyProvider, plan: Plan, op: ImportDependency): Either[MissingImport, Seq[NewObjectOp]]
}
