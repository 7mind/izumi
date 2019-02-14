package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.provisioning.NewObjectOp

trait JustExecutor {
  def execute(step: ExecutableOp): Seq[NewObjectOp]
}
