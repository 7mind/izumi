package izumi.distage.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.distage.model.provisioning.strategies.ContextStrategy
import izumi.functional.quasi.QuasiIO
import izumi.reflect.TagK

class ContextStrategyDefaultImpl extends ContextStrategy {
  override def prepareContext[F[_]: TagK: QuasiIO](context: ProvisioningKeyProvider, op: WiringOp.LocalContext): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = ???
}
