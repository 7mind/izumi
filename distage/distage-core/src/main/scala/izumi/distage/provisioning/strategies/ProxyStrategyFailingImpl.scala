package izumi.distage.provisioning.strategies

import izumi.distage.model.exceptions.NoopProvisionerImplCalled
import izumi.distage.model.monadic.DIEffect
import izumi.distage.model.plan.ExecutableOp.ProxyOp
import izumi.distage.model.provisioning.strategies.ProxyStrategy
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import izumi.fundamentals.platform.language.Quirks

class ProxyStrategyFailingImpl extends ProxyStrategy {
  override def initProxy[F[_]: TagK: DIEffect](context: ProvisioningKeyProvider, executor: OperationExecutor, initProxy: ProxyOp.InitProxy): F[Seq[NewObjectOp]] = {
    Quirks.discard(context, executor)
    throw new NoopProvisionerImplCalled(s"ProxyStrategyFailingImpl does not support proxies, failed op: $initProxy", this)
  }

  override def makeProxy(context: ProvisioningKeyProvider, makeProxy: ProxyOp.MakeProxy): Seq[NewObjectOp] = {
    Quirks.discard(context)
    throw new NoopProvisionerImplCalled(s"ProxyStrategyFailingImpl does not support proxies, failed op: $makeProxy", this)
  }
}
