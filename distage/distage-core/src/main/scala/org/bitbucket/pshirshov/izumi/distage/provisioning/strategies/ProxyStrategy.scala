package org.bitbucket.pshirshov.izumi.distage.provisioning.strategies

import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, OperationExecutor, ProvisioningContext}

trait ProxyStrategy {
  def initProxy(context: ProvisioningContext, executor: OperationExecutor, i: ProxyOp.InitProxy): Seq[OpResult]

  def makeProxy(context: ProvisioningContext, m: ProxyOp.MakeProxy): Seq[OpResult]
}

