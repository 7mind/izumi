package izumi.distage.model.provisioning.strategies

import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.exceptions.interpretation.ProvisionerIssue
import izumi.distage.model.plan.ExecutableOp.MonadicOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.reflect.TagK

trait EffectStrategy {
  def executeEffect[F[_]: TagK: QuasiIO](context: ProvisioningKeyProvider, op: MonadicOp.ExecuteEffect): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
