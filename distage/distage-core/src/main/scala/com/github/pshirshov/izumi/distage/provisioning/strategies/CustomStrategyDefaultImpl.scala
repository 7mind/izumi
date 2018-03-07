package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.CustomOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.CustomStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningContext}

class CustomStrategyDefaultImpl extends CustomStrategy {
  override def handle(context: ProvisioningContext, op: CustomOp): Seq[OpResult] = {
    throw new DIException(s"No handle for CustomOp for ${op.target}, defs: ${op.data.customDef}", null)
  }
}

object CustomStrategyDefaultImpl {
  final val instance = new CustomStrategyDefaultImpl()
}
