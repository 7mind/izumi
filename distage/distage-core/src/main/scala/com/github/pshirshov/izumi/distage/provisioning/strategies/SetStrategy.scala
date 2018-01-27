package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}

trait SetStrategy {

  def makeSet(context: ProvisioningContext, op: ExecutableOp.SetOp.CreateSet): Seq[OpResult.NewInstance]

  def addToSet(context: ProvisioningContext, op: ExecutableOp.SetOp.AddToSet): Seq[OpResult.UpdatedSet]


}

