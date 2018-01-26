package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.provisioning.{OpResult, OperationExecutor, ProvisioningContext}


trait FactoryStrategy {
  def makeFactory(context: ProvisioningContext, executor: OperationExecutor, f: WiringOp.InstantiateFactory): Seq[OpResult]

}

