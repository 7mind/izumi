package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ProxyStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, OperationExecutor, ProvisioningContext}

class ProxyStrategyFailingImpl extends ProxyStrategy {
  override def initProxy(context: ProvisioningContext, executor: OperationExecutor, initProxy: ProxyOp.InitProxy): Seq[OpResult] = {
    throw new DIException(s"ProxyStrategyFailingImpl does not support proxies, failed op: $initProxy", null)

  }

  override def makeProxy(context: ProvisioningContext, makeProxy: ProxyOp.MakeProxy): Seq[OpResult] = {
    throw new DIException(s"ProxyStrategyFailingImpl does not support proxies, failed op: $makeProxy", null)
  }
}
