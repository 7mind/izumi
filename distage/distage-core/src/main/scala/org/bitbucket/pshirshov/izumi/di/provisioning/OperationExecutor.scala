package org.bitbucket.pshirshov.izumi.di.provisioning

import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp

trait OperationExecutor {
  def execute(step: ExecutableOp): Seq[OpResult]
}
