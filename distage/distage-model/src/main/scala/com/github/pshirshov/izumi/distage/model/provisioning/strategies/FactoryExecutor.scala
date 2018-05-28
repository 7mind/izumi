package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.provisioning.OpResult

trait FactoryExecutor {
  type MethodId = Int

  def execute(methodId: MethodId, args: Seq[Any]): Seq[OpResult]
}
