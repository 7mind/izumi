package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.MissingInstanceException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ImportDependency
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ImportStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{FactoryExecutor, OpResult, ProvisioningContext}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

class ImportStrategyDefaultImpl extends ImportStrategy {
  override def importDependency(context: ProvisioningContext, op: ImportDependency): Seq[OpResult] = {
    import op._

    context.importKey(target) match {
      case Some(v) =>
        Seq(OpResult.NewImport(target, v))
      // support FactoryStrategyMacro
      case _ if target == RuntimeDIUniverse.DIKey.get[FactoryExecutor] =>
        Seq(OpResult.DoNothing())
      case _ =>
        throw new MissingInstanceException(s"Instance is not available in the context: $target. " +
          s"required by refs: $references", target)
    }
  }
}


class ImportStrategyFailingImpl extends ImportStrategy {
  override def importDependency(context: ProvisioningContext, op: ImportDependency): Seq[OpResult] = {
    Quirks.discard(context)

    import op._
    throw new MissingInstanceException(s"Imports are disabled and instance is not available in the context: $target. required by refs: $references", target)
  }
}

