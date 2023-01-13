package izumi.distage.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.plan.ExecutableOp.ProxyOp
import izumi.distage.model.provisioning.strategies.ProxyStrategy
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.reflect.TagK

import scala.annotation.unused

class ProxyStrategyFailingImpl extends ProxyStrategy {
  override def initProxy[F[_]: TagK: QuasiIO](
    @unused context: ProvisioningKeyProvider,
    @unused executor: OperationExecutor,
    initProxy: ProxyOp.InitProxy,
  ): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    implicitly[QuasiIO[F]].pure(Left(ProvisionerIssue.ProxyStrategyFailingImplCalled(initProxy.target, this)))
  }

  override def makeProxy[F[_]: TagK: QuasiIO](@unused context: ProvisioningKeyProvider, makeProxy: ProxyOp.MakeProxy): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    implicitly[QuasiIO[F]].pure(Left(ProvisionerIssue.ProxyStrategyFailingImplCalled(makeProxy.target, this)))
  }
}
