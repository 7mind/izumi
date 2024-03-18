package izumi.distage.model.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.quasi.QuasiIO
import izumi.distage.model.plan.ExecutableOp.CreateSet
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.reflect.TagK

trait SetStrategy {
  def makeSet[F[_]: TagK: QuasiIO](context: ProvisioningKeyProvider, op: CreateSet): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
