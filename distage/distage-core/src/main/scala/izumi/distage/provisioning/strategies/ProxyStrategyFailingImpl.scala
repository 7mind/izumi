package izumi.distage.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.bio.IO2
import izumi.distage.model.plan.ExecutableOp.ProxyOp
import izumi.distage.model.provisioning.strategies.ProxyStrategy
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.reflect.TagKK

import scala.annotation.unused

class ProxyStrategyFailingImpl extends ProxyStrategy {
  override def initProxy[F[+_, +_]: TagKK: IO2](
    @unused context: ProvisioningKeyProvider,
    @unused executor: OperationExecutor,
    initProxy: ProxyOp.InitProxy,
  ): F[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    implicitly[IO2[F]].pure(Left(ProvisionerIssue.ProxyStrategyFailingImplCalled(initProxy.target, initProxy.proxy, this)))
  }

  override def makeProxy[F[+_, +_]: TagKK: IO2](@unused context: ProvisioningKeyProvider, makeProxy: ProxyOp.MakeProxy): F[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    implicitly[IO2[F]].pure(Left(ProvisionerIssue.ProxyStrategyFailingImplCalled(makeProxy.target, makeProxy, this)))
  }
}
