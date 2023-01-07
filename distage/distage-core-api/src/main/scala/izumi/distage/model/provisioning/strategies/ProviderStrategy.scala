package izumi.distage.model.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}

trait ProviderStrategy {
  def callProvider[F[_]: QuasiIO](context: ProvisioningKeyProvider, op: WiringOp.CallProvider): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
