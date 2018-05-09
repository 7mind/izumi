package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.CreateSet
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningContext}

trait SetStrategy {

  def makeSet(context: ProvisioningContext, op: CreateSet): Seq[OpResult.NewInstance]

}

