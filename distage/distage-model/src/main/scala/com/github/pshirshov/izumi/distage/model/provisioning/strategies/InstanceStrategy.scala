package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.{ContextAssignment, ProvisioningKeyProvider}

trait InstanceStrategy {
  def getInstance(context: ProvisioningKeyProvider, op: WiringOp.ReferenceInstance): Seq[ContextAssignment]
  def getInstance(context: ProvisioningKeyProvider, op: WiringOp.ReferenceKey): Seq[ContextAssignment]
}
