package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp
import com.github.pshirshov.izumi.distage.model.provisioning.{ExecutableOpResult, OperationExecutor, ProvisioningKeyProvider}

trait ProxyStrategy {
  def initProxy(context: ProvisioningKeyProvider, executor: OperationExecutor, initProxy: ProxyOp.InitProxy): Seq[ExecutableOpResult]

  def makeProxy(context: ProvisioningKeyProvider, makeProxy: ProxyOp.MakeProxy): Seq[ExecutableOpResult]
}

