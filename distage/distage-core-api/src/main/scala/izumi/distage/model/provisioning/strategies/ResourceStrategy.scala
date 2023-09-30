package izumi.distage.model.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.quasi.QuasiIO
import izumi.distage.model.plan.ExecutableOp.MonadicOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.reflect.TagK

trait ResourceStrategy {
  def allocateResource[F[_]: TagK: QuasiIO](context: ProvisioningKeyProvider, op: MonadicOp.AllocateResource): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
