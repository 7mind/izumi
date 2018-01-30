package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, OperationExecutor, ProvisioningContext}

trait ProxyStrategy {
  def initProxy(context: ProvisioningContext, executor: OperationExecutor, initProxy: ProxyOp.InitProxy): Seq[OpResult]

  def makeProxy(context: ProvisioningContext, makeProxy: ProxyOp.MakeProxy): Seq[OpResult]
}

