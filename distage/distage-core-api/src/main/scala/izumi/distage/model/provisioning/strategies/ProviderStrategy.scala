package izumi.distage.model.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.bio.IO1
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}

trait ProviderStrategy {
  def callProvider[F[_]: IO1](context: ProvisioningKeyProvider, op: WiringOp.CallProvider): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
