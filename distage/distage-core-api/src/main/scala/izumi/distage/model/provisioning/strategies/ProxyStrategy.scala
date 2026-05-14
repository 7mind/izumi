package izumi.distage.model.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.bio.IO1
import izumi.distage.model.plan.ExecutableOp.ProxyOp
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.reflect.TagK

trait ProxyStrategy {
  def makeProxy[F[_]: TagK: IO1](context: ProvisioningKeyProvider, makeProxy: ProxyOp.MakeProxy): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
  def initProxy[F[_]: TagK: IO1](
    context: ProvisioningKeyProvider,
    executor: OperationExecutor,
    initProxy: ProxyOp.InitProxy,
  ): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
