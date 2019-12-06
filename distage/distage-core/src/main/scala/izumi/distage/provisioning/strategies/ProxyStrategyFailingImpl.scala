package izumi.distage.provisioning.strategies

import izumi.distage.model.effect.DIEffect
import izumi.distage.model.exceptions.NoopProvisionerImplCalled
import izumi.distage.model.plan.ExecutableOp.ProxyOp
import izumi.distage.model.provisioning.strategies.ProxyStrategy
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider, WiringExecutor}
import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.reflection.Tags.TagK

class ProxyStrategyFailingImpl extends ProxyStrategy {
  override def initProxy[F[_]: TagK: DIEffect](@unused context: ProvisioningKeyProvider, @unused executor: OperationExecutor, initProxy: ProxyOp.InitProxy): F[Seq[NewObjectOp]] = {
    throw new NoopProvisionerImplCalled(s"ProxyStrategyFailingImpl does not support proxies, failed op: $initProxy", this)
  }
  override def makeProxy(@unused context: ProvisioningKeyProvider, @unused executor: WiringExecutor, makeProxy: ProxyOp.MakeProxy): Seq[NewObjectOp] = {
    throw new NoopProvisionerImplCalled(s"ProxyStrategyFailingImpl does not support proxies, failed op: $makeProxy", this)
  }
}
