package izumi.distage.model.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.bio.IO2
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.reflect.TagKK

trait InstanceStrategy {
  def getInstance[F[+_, +_]: TagKK: IO2](context: ProvisioningKeyProvider, op: WiringOp.UseInstance): F[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]]]
  def getInstance[F[+_, +_]: TagKK: IO2](context: ProvisioningKeyProvider, op: WiringOp.ReferenceKey): F[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
