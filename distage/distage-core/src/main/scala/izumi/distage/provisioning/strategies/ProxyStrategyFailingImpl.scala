package izumi.distage.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.bio.IO1
import izumi.distage.model.plan.ExecutableOp.ProxyOp
import izumi.distage.model.provisioning.strategies.ProxyStrategy
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.reflect.TagK

import scala.annotation.unused

class ProxyStrategyFailingImpl extends ProxyStrategy {
  override def initProxy[F[_]: TagK: IO1](
    @unused context: ProvisioningKeyProvider,
    @unused executor: OperationExecutor,
    initProxy: ProxyOp.InitProxy,
  ): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    IO1[F].pure(Left(ProvisionerIssue.ProxyStrategyFailingImplCalled(initProxy.target, initProxy.proxy, this)))
  }

  override def makeProxy[F[_]: TagK: IO1](@unused context: ProvisioningKeyProvider, makeProxy: ProxyOp.MakeProxy): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    IO1[F].pure(Left(ProvisionerIssue.ProxyStrategyFailingImplCalled(makeProxy.target, makeProxy, this)))
  }
}
