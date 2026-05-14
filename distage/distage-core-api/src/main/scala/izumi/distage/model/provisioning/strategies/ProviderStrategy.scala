package izumi.distage.model.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.bio.IO2
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}

trait ProviderStrategy {
  def callProvider[F[+_, +_]: IO2](context: ProvisioningKeyProvider, op: WiringOp.CallProvider): F[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
