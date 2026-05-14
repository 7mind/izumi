package izumi.distage.model.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.bio.IO2
import izumi.distage.model.plan.ExecutableOp.MonadicOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.reflect.TagKK

trait EffectStrategy {
  def executeEffect[F[+_, +_]: TagKK: IO2](context: ProvisioningKeyProvider, op: MonadicOp.ExecuteEffect): F[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
