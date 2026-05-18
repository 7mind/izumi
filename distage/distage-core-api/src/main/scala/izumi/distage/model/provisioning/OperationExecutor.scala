package izumi.distage.model.provisioning

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.bio.IO2
import izumi.distage.model.plan.ExecutableOp.NonImportOp
import izumi.reflect.TagKK

trait OperationExecutor {
  def execute[F[+_, +_]: TagKK: IO2](context: ProvisioningKeyProvider, step: NonImportOp): F[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
