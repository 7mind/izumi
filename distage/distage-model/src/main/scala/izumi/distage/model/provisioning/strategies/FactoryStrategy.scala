package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider, WiringExecutor}

trait FactoryStrategy {
  def makeFactory(context: ProvisioningKeyProvider, executor: WiringExecutor, op: WiringOp.InstantiateFactory): Seq[NewObjectOp]
}

