package izumi.distage.model.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.bio.IO2
import izumi.distage.model.plan.ExecutableOp.CreateSet
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.reflect.TagKK

trait SetStrategy {
  def makeSet[F[+_, +_]: TagKK: IO2](context: ProvisioningKeyProvider, op: CreateSet): F[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
