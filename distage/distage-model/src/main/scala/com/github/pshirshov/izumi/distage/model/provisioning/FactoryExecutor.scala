package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

private [distage] trait FactoryExecutor {
  def execute(args: Map[RuntimeUniverse.DIKey, Any], step: WiringOp): Seq[OpResult]
}
