package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.SetOp
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningContext}

trait SetStrategy {

  def makeSet(context: ProvisioningContext, op: SetOp.CreateSet): Seq[OpResult.NewInstance]

  //def addToSet(context: ProvisioningContext, op: SetOp.AddToSet): Seq[OpResult.UpdatedSet]


}

