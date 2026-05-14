package izumi.distage.model.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.functional.bio.IO2
import izumi.reflect.TagKK

trait SubcontextStrategy {
  def prepareSubcontext[F[+_, +_]: TagKK: IO2](context: ProvisioningKeyProvider, op: WiringOp.CreateSubcontext): F[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
