package izumi.distage.model.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.quasi.QuasiIO
import izumi.distage.model.plan.ExecutableOp.MonadicOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.reflect.TagK

trait EffectStrategy {
  def executeEffect[F[_]: TagK: QuasiIO](context: ProvisioningKeyProvider, op: MonadicOp.ExecuteEffect): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
