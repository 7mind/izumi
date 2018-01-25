package org.bitbucket.pshirshov.izumi.distage.provisioning.strategies

import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp
import org.bitbucket.pshirshov.izumi.distage.provisioning.OpResult

trait JustExecutor {
  def execute(step: ExecutableOp): Seq[OpResult]
}
