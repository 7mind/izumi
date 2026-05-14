package izumi.distage.model.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.functional.bio.IO1
import izumi.reflect.TagK

trait SubcontextStrategy {
  def prepareSubcontext[F[_]: TagK: IO1](context: ProvisioningKeyProvider, op: WiringOp.CreateSubcontext): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
