package org.bitbucket.pshirshov.izumi.distage.provisioning.strategies

import org.bitbucket.pshirshov.izumi.distage.model.exceptions.DIException
import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}

class CustomStrategyDefaultImpl extends CustomStrategy {
  override def handle(context: ProvisioningContext, op: ExecutableOp.CustomOp): Seq[OpResult] = {
    throw new DIException(s"No handle for CustomOp for ${op.target}, defs: ${op.data.customDef}", null)
  }
}

object CustomStrategyDefaultImpl {
  final val instance = new CustomStrategyDefaultImpl()
}