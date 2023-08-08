package izumi.distage.model.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.functional.quasi.QuasiIO
import izumi.reflect.TagK

trait ContextStrategy {
  def prepareContext[F[_]: TagK: QuasiIO](context: ProvisioningKeyProvider, op: WiringOp.LocalContext): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
