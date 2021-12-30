package izumi.distage.model.provisioning

import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.exceptions.interpretation.ProvisionerIssue
import izumi.distage.model.plan.ExecutableOp.NonImportOp
import izumi.reflect.TagK

trait OperationExecutor {
  def execute[F[_]: TagK: QuasiIO](context: ProvisioningKeyProvider, step: NonImportOp): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
