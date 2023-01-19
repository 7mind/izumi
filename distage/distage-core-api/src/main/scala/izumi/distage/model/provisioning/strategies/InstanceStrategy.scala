package izumi.distage.model.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.reflect.TagK

trait InstanceStrategy {
  def getInstance[F[_]: TagK: QuasiIO](context: ProvisioningKeyProvider, op: WiringOp.UseInstance): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
  def getInstance[F[_]: TagK: QuasiIO](context: ProvisioningKeyProvider, op: WiringOp.ReferenceKey): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
