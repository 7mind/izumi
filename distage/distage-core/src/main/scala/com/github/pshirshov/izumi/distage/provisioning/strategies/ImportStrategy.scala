package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}

trait ImportStrategy {
  def importDependency(context: ProvisioningContext, op: ExecutableOp.ImportDependency): Seq[OpResult]

}
