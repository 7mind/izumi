package izumi.distage.model.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.bio.IO1
import izumi.distage.model.plan.ExecutableOp.CreateSet
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.reflect.TagK

trait SetStrategy {
  def makeSet[F[_]: TagK: IO1](context: ProvisioningKeyProvider, op: CreateSet): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
