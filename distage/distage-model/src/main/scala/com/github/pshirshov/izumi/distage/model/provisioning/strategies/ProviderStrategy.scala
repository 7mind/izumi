package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}

trait ProviderStrategy {
  def callProvider(context: ProvisioningKeyProvider, op: WiringOp.CallProvider): Seq[NewObjectOp.NewInstance]
}
