package izumi.distage.model.provisioning.strategies

import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.exceptions.interpretation.ProvisionerIssue
import izumi.distage.model.plan.ExecutableOp.ProxyOp
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.reflect.TagK

trait ProxyStrategy {
  def makeProxy[F[_]: TagK: QuasiIO](context: ProvisioningKeyProvider, makeProxy: ProxyOp.MakeProxy): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
  def initProxy[F[_]: TagK: QuasiIO](
    context: ProvisioningKeyProvider,
    executor: OperationExecutor,
    initProxy: ProxyOp.InitProxy,
  ): F[Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
