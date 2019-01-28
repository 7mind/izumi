package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.CreateSet
import com.github.pshirshov.izumi.distage.model.provisioning.{ContextAssignment, ProvisioningKeyProvider}

trait SetStrategy {

  def makeSet(context: ProvisioningKeyProvider, op: CreateSet): Seq[ContextAssignment.NewInstance]

}

