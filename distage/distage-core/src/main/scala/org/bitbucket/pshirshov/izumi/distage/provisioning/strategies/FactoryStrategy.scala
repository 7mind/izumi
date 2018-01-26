package org.bitbucket.pshirshov.izumi.distage.provisioning.strategies

import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, OperationExecutor, ProvisioningContext}


trait FactoryStrategy {
  def makeFactory(context: ProvisioningContext, executor: OperationExecutor, f: WiringOp.InstantiateFactory): Seq[OpResult]

}

