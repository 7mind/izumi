package izumi.distage.model.provisioning.strategies

import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.exceptions.interpretation.ProvisionerIssue
import izumi.distage.model.plan.ExecutableOp.CreateSet
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.reflect.TagK

trait SetStrategy {
  def makeSet[F[_]: TagK: QuasiIO](context: ProvisioningKeyProvider, op: CreateSet): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
