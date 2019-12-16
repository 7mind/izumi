package izumi.distage.provisioning.strategies

import izumi.distage.model.exceptions.MissingInstanceException
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.distage.model.provisioning.strategies.ImportStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider, WiringExecutor}
import izumi.fundamentals.platform.language.unused

class ImportStrategyDefaultImpl extends ImportStrategy {
  override def importDependency(context: ProvisioningKeyProvider, @unused executor: WiringExecutor, op: ImportDependency): Seq[NewObjectOp] = {
    context.importKey(op.target) match {
      case Some(v) =>
        Seq(NewObjectOp.NewImport(op.target, v))
      case _ =>
        throw new MissingInstanceException(MissingInstanceException.format(op.target, op.references), op.target)
    }
  }
}
