package izumi.distage.provisioning.strategies

import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.exceptions.NoopProvisionerImplCalled
import izumi.distage.model.plan.ExecutableOp.ProxyOp
import izumi.distage.model.provisioning.strategies.ProxyStrategy
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.fundamentals.platform.language.unused
import izumi.reflect.TagK

class ProxyStrategyFailingImpl extends ProxyStrategy {
  override def initProxy[F[_]: TagK: QuasiIO](
    @unused context: ProvisioningKeyProvider,
    @unused executor: OperationExecutor,
    initProxy: ProxyOp.InitProxy,
  ): F[Seq[NewObjectOp]] = {
    throw new NoopProvisionerImplCalled(s"ProxyStrategyFailingImpl does not support proxies, failed op: $initProxy", this)
  }
  override def makeProxy(@unused context: ProvisioningKeyProvider, makeProxy: ProxyOp.MakeProxy): Seq[NewObjectOp] = {
    throw new NoopProvisionerImplCalled(s"ProxyStrategyFailingImpl does not support proxies, failed op: $makeProxy", this)
  }
}
