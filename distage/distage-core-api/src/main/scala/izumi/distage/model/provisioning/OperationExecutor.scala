package izumi.distage.model.provisioning

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.bio.IO1
import izumi.distage.model.plan.ExecutableOp.NonImportOp
import izumi.reflect.TagK

trait OperationExecutor {
  def execute[F[_]: TagK: IO1](context: ProvisioningKeyProvider, step: NonImportOp): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
