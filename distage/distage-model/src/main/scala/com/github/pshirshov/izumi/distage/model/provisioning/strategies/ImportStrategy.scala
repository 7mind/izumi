package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningContext}

trait ImportStrategy {
  def importDependency(context: ProvisioningContext, op: ExecutableOp.ImportDependency): Seq[OpResult]

}
