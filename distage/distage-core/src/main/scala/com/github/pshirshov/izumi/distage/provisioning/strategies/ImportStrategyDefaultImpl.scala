package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.MissingInstanceException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ImportDependency
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.{FactoryExecutor, ImportStrategy}
import com.github.pshirshov.izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

class ImportStrategyDefaultImpl extends ImportStrategy {
  override def importDependency(context: ProvisioningKeyProvider, op: ImportDependency): Seq[NewObjectOp] = {
    import op._

    context.importKey(target) match {
      case Some(v) =>
        Seq(NewObjectOp.NewImport(target, v))
      // support FactoryStrategyMacro
      case _ if target == RuntimeDIUniverse.DIKey.get[FactoryExecutor] =>
        Seq(NewObjectOp.DoNothing())
      case _ =>
        throw new MissingInstanceException(s"Instance is not available in the object graph: $target. " +
          s"required by refs: $references", target)
    }
  }
}


class ImportStrategyFailingImpl extends ImportStrategy {
  override def importDependency(context: ProvisioningKeyProvider, op: ImportDependency): Seq[NewObjectOp] = {
    Quirks.discard(context)

    import op._
    throw new MissingInstanceException(s"Imports are disabled and instance is not available in the object graph: $target. required by refs: $references", target)
  }
}

