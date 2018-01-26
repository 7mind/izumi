package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.MissingInstanceException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}

class ImportStrategyDefaultImpl extends ImportStrategy {
  override def importDependency(context: ProvisioningContext, op: ExecutableOp.ImportDependency): Seq[OpResult] = {
    import op._

    context.importKey(target) match {
      case Some(v) =>
        Seq(OpResult.NewImport(target, v))
      case _ =>
        throw new MissingInstanceException(s"Instance is not available in the context: $target. " +
          s"required by refs: $references", target)
    }
  }
}

object ImportStrategyDefaultImpl {
  final val instance = new ImportStrategyDefaultImpl()
}
