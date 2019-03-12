package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.NoopProvisionerImplCalled
import com.github.pshirshov.izumi.distage.model.monadic.DIMonad
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ProxyStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

class ProxyStrategyFailingImpl extends ProxyStrategy {
  override def initProxy[F[_]: TagK: DIMonad](context: ProvisioningKeyProvider, executor: OperationExecutor, initProxy: ProxyOp.InitProxy): F[Seq[NewObjectOp]] = {
    Quirks.discard(context, executor)
    throw new NoopProvisionerImplCalled(s"ProxyStrategyFailingImpl does not support proxies, failed op: $initProxy", this)
  }

  override def makeProxy(context: ProvisioningKeyProvider, makeProxy: ProxyOp.MakeProxy): Seq[NewObjectOp] = {
    Quirks.discard(context)
    throw new NoopProvisionerImplCalled(s"ProxyStrategyFailingImpl does not support proxies, failed op: $makeProxy", this)
  }
}
