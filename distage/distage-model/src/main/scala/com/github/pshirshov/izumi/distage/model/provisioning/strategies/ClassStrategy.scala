package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.{ContextAssignment, ProvisioningKeyProvider}

trait ClassStrategy {
  def instantiateClass(context: ProvisioningKeyProvider, op: WiringOp.InstantiateClass): Seq[ContextAssignment.NewInstance]

}

